#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""回填压缩 R2 上 meal 表引用的历史生成图

背景：文生图早期没有压缩，R2 上堆积了一批 2~3MB 的 PNG。本脚本把 meal 相关表
（meal_dish.cover / meal_order_item.dish_cover / meal_proposal.image / meal_review.images）
引用到的图片逐张下载 → 压缩 → 覆盖回原对象键。

压缩配方与后端 AiImageCompressor 保持一致：长边缩到 1024px，JPEG 质量 0.80，
兜底上限 500KB（超了依次降质量、再缩边）。

安全约定：
- 只处理 meal 表引用到、且落在 R2 公开域名下的图片，其它一律不碰；
- 覆盖同一对象键，URL 不变、无需改库；Content-Type 改为 image/jpeg；
- 默认 dry-run，只打印不写入，确认无误后加 --apply 才真正覆盖；
- 已经小于兜底上限的对象直接跳过，可重复执行。

用法：
    python scripts/compress-r2-meal-images.py            # 预演（meal 表引用）
    python scripts/compress-r2-meal-images.py --apply    # 真正覆盖
    python scripts/compress-r2-meal-images.py --prefix img/ai/ --apply   # 按前缀处理全部对象
"""

import argparse
import io
import re
import sys
from pathlib import Path

import boto3
import pymysql
from PIL import Image

# 与后端 AiImageCompressor 对齐的压缩参数
MAX_EDGE = 1024
QUALITY_LADDER = [0.80, 0.75, 0.70, 0.60, 0.50]
TARGET_BYTES = 500 * 1024
SCALE_STEP = 0.8
MAX_SCALE_ROUNDS = 4

ROOT = Path(__file__).resolve().parents[1]
ENV_FILE = ROOT / ".env"


def load_env(path):
    """读取 .env 为字典"""
    env = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        env[key.strip()] = value.strip()
    return env


def load_config(env):
    """从 sys_config 取 R2 配置"""
    match = re.search(r"//([^:]+):(\d+)/([^?]+)", env["DB_URL"])
    conn = pymysql.connect(
        host=match.group(1), port=int(match.group(2)),
        user=env["DB_USERNAME"], password=env["DB_PASSWORD"],
        database=match.group(3), charset="utf8mb4",
    )
    try:
        with conn.cursor() as cur:
            cur.execute(
                "select config_key, config_value from sys_config "
                "where config_key like 'sys.storage.r2.%'"
            )
            r2 = dict(cur.fetchall())
    finally:
        conn.close()
    return match.group(3), r2


def collect_meal_urls(env, database):
    """枚举 meal 表中引用的所有图片 URL"""
    match = re.search(r"//([^:]+):(\d+)/([^?]+)", env["DB_URL"])
    conn = pymysql.connect(
        host=match.group(1), port=int(match.group(2)),
        user=env["DB_USERNAME"], password=env["DB_PASSWORD"],
        database=database, charset="utf8mb4",
    )
    urls = set()
    try:
        with conn.cursor() as cur:
            cur.execute(
                "select table_name, column_name from information_schema.columns "
                "where table_schema = %s and table_name like 'meal%%' "
                "and (column_name like '%%cover%%' or column_name like '%%url%%' "
                "or column_name like '%%image%%')",
                (database,),
            )
            columns = cur.fetchall()
            for table, column in columns:
                cur.execute(
                    f"select `{column}` from `{table}` "
                    f"where `{column}` is not null and `{column}` <> ''"
                )
                for (value,) in cur.fetchall():
                    urls.update(re.findall(r"https?://[^\s,\"']+", str(value)))
    finally:
        conn.close()
    return urls


def list_prefix_objects(s3, bucket, prefix):
    """列出桶内指定前缀下的全部对象键"""
    keys = []
    paginator = s3.get_paginator("list_objects_v2")
    for page in paginator.paginate(Bucket=bucket, Prefix=prefix):
        for obj in page.get("Contents", []):
            keys.append(obj["Key"])
    return keys


def compress(data):
    """压缩图片，返回 (字节, 宽, 高, 是否压缩过)"""
    if len(data) <= TARGET_BYTES:
        return data, None, None, False
    image = Image.open(io.BytesIO(data))
    has_alpha = image.mode in ("RGBA", "LA", "P")
    if has_alpha:
        background = Image.new("RGB", image.size, (255, 255, 255))
        background.paste(image.convert("RGBA"), mask=image.convert("RGBA").split()[-1])
        image = background
    else:
        image = image.convert("RGB")

    best = data
    best_size = None
    edge = max(image.size)
    scale = min(1.0, MAX_EDGE / edge)
    for _ in range(MAX_SCALE_ROUNDS + 1):
        if scale >= 1.0:
            canvas = image
        else:
            canvas = image.resize(
                (max(1, round(image.width * scale)), max(1, round(image.height * scale))),
                Image.LANCZOS,
            )
        for quality in QUALITY_LADDER:
            buffer = io.BytesIO()
            canvas.save(buffer, format="JPEG", quality=int(quality * 100), optimize=True)
            result = buffer.getvalue()
            if len(result) < len(best):
                best = result
                best_size = canvas.size
            if len(result) <= TARGET_BYTES:
                return result, canvas.width, canvas.height, True
        scale *= SCALE_STEP
    return best, (best_size or image.size)[0], (best_size or image.size)[1], True


def human(size):
    return f"{size / 1024:.0f}K" if size < 1024 * 1024 else f"{size / 1024 / 1024:.2f}M"


def main():
    parser = argparse.ArgumentParser(description="回填压缩 R2 上 meal 表引用的历史生成图")
    parser.add_argument("--apply", action="store_true", help="真正覆盖对象；缺省只预演")
    parser.add_argument("--limit", type=int, default=0, help="只处理前 N 张，便于试跑")
    parser.add_argument("--backup", metavar="DIR", default="",
                        help="覆盖前把原图另存到该目录，作为回滚后路")
    parser.add_argument("--prefix", metavar="PREFIX", default="",
                        help="改为处理桶内该前缀下的全部对象（如 img/ai/），不再依赖 meal 表引用")
    args = parser.parse_args()

    env = load_env(ENV_FILE)
    database, r2 = load_config(env)
    bucket = r2["sys.storage.r2.bucket"]
    public_url = r2["sys.storage.r2.publicUrl"].rstrip("/")

    s3 = boto3.client(
        "s3",
        endpoint_url=r2["sys.storage.r2.endpoint"],
        aws_access_key_id=r2["sys.storage.r2.accessKeyId"],
        aws_secret_access_key=r2["sys.storage.r2.secretAccessKey"],
        region_name="auto",
        config=boto3.session.Config(
            signature_version="s3v4", s3={"addressing_style": "path"}
        ),
    )

    targets, skipped = [], []
    if args.prefix:
        keys = list_prefix_objects(s3, bucket, args.prefix)
        print(f"前缀 {args.prefix} 下共 {len(keys)} 个对象")
        targets = [(public_url + "/" + key, key) for key in keys]
    else:
        urls = collect_meal_urls(env, database)
        print(f"meal 表引用图片：{len(urls)} 个 URL（去重后）")
        for url in sorted(urls):
            if url.startswith(public_url + "/"):
                targets.append((url, url[len(public_url) + 1:]))
            else:
                skipped.append(url)

    if skipped:
        print(f"跳过 {len(skipped)} 个非本 R2 域名的地址：")
        for url in skipped:
            print(f"  - {url}")

    if args.limit:
        targets = targets[: args.limit]

    print(f"\n待处理 {len(targets)} 个对象，模式：{'覆盖写入' if args.apply else '预演（不写入）'}\n")
    print(f"{'对象键':<52}{'原始':>10}{'压缩后':>10}{'节省':>9}")

    total_before = total_after = 0
    changed = 0
    for url, key in targets:
        try:
            obj = s3.get_object(Bucket=bucket, Key=key)
            data = obj["Body"].read()
        except Exception as exc:  # noqa: BLE001
            print(f"{key:<52}{'下载失败':>10}  {exc}")
            continue

        before = len(data)
        total_before += before
        try:
            result, width, height, compressed = compress(data)
        except Exception as exc:  # noqa: BLE001
            print(f"{key:<52}{human(before):>10}{'压缩失败':>10}  {exc}")
            total_after += before
            continue

        after = len(result)
        total_after += after
        note = "" if compressed else "（已达标，跳过）"
        dims = f"{width}x{height}" if compressed else "-"

        if compressed and args.apply:
            if args.backup:
                backup_path = Path(args.backup) / key
                backup_path.parent.mkdir(parents=True, exist_ok=True)
                backup_path.write_bytes(data)
            s3.put_object(
                Bucket=bucket, Key=key, Body=result,
                ContentType="image/jpeg",
            )
            changed += 1

        print(f"{key:<52}{human(before):>10}{human(after):>10}"
              f"{(1 - after / before) * 100:>8.0f}%  {dims}{note}")

    print(f"\n合计：{human(total_before)} → {human(total_after)}"
          f"（节省 {human(total_before - total_after)}，{(1 - total_after / total_before) * 100:.0f}%）")
    if args.apply:
        print(f"已覆盖 {changed} 个对象")
    else:
        print("这是预演，未写入任何对象；确认无误后加 --apply 执行")


if __name__ == "__main__":
    sys.exit(main())
