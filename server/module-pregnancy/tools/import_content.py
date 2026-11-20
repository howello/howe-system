"""将孕期原始素材整理为模块资源。用法：python import_content.py <数据目录>。依赖 Pillow。"""

import argparse
import hashlib
import io
import json
import re
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

from PIL import Image


RESOURCE_DIR = Path(__file__).resolve().parents[1] / "src/main/resources/pregnancy"
ASSET_PREFIX = "/pregnancy/content/images/"
FOOD_RATINGS = {
    "放心吃": "RECOMMENDED",
    "能吃": "ALLOWED",
    "少吃": "LIMIT",
    "慎吃": "CAUTION",
    "不能吃": "AVOID",
}
WEEK_IMAGE_LABELS = {
    "week_change_content_iv": "胎宝宝示意",
    "ivFruitIcon": "大小对照",
    "livCaiChao": "彩超示例",
    "livBChao": "B 超示例",
    "iv_subscribe_banner_bg": "本周配图",
}


def read_json(path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def write_json(name, data):
    content = json.dumps(data, ensure_ascii=False, indent=2)
    (RESOURCE_DIR / name).write_text(content + "\n", encoding="utf-8", newline="\n")


def clean(text):
    return text.replace("\r\n", "\n").replace("\n\r", "\n").replace("\r", "\n").strip()


def unique(texts):
    return list(dict.fromkeys(value for text in texts if (value := clean(text))))


def rows_with_images(sources):
    return [
        row for name, data in sources.items()
        for row in (data if not name.startswith("能不能吃") else food_rows(data))
    ]


def import_images(source_dir, rows):
    """按 PNG 字节去重，将原图转为按内容寻址的 WebP。"""
    output = RESOURCE_DIR / "images"
    output.mkdir(exist_ok=True)
    paths = {path for row in rows for path in row.get("图片", [])}
    disk_paths = {path.relative_to(source_dir).as_posix() for path in source_dir.rglob("*.png")}
    if paths != disk_paths:
        raise ValueError(f"素材图片引用不一致：缺少 {paths - disk_paths}，未引用 {disk_paths - paths}")
    groups = {}
    for path in sorted(paths):
        digest = hashlib.sha256((source_dir / path).read_bytes()).hexdigest()
        groups.setdefault(digest, []).append(path)

    def convert(group):
        with Image.open(source_dir / group[0]) as image:
            image = image.convert("RGB")
            if Path(group[0]).stem.split("-")[0] == "详情图片":
                image.thumbnail((900, 500), Image.Resampling.LANCZOS)
            buffer = io.BytesIO()
            image.save(buffer, "WEBP", quality=82, method=4)
        data = buffer.getvalue()
        filename = hashlib.sha256(data).hexdigest() + ".webp"
        target = output / filename
        if not target.exists():
            target.write_bytes(data)
        return [(path, ASSET_PREFIX + filename) for path in group]

    with ThreadPoolExecutor(max_workers=6) as executor:
        return dict(pair for group in executor.map(convert, groups.values()) for pair in group)


def daily(rows):
    days = {}
    for row in rows:
        age = re.fullmatch(r"孕(\d+)周(?:(\d+)天)?", row["孕周及天数"])
        day = int(age[1]) * 7 + int(age[2] or 0)
        days[day] = clean(row["正文"])
    if set(days) != set(range(1, 295)) or len(rows) != len(days):
        raise ValueError("每日变化的孕日不连续或存在重复")
    return dict(sorted(days.items()))


def weekly(rows, images):
    """保留正文、发育参数、来源与配图，移除采集时的日期和原应用操作文案。"""
    weeks = {}
    stats = re.compile(r"^(胎宝宝[≈约]|顶臀长[：:]|身长[：:]|体重[：:])")
    labels = {"彩超中的宝宝", "B超中的宝宝", "三维彩超和B超中的宝宝", "发育数据",
              "孕早期", "孕中期", "孕晚期", "邀请准爸爸", "告诉准爸爸，孕期他该做这些事"}
    for row in rows:
        week = int(re.fullmatch(r"孕(\d+)周", row["孕周"])[1])
        sections, measurements, sources = [], [], []
        for section in row["章节"]:
            title = section["标题"]
            paragraphs = []
            for text in unique(section["内容"]):
                if text == "24h问医生":
                    break
                if text.startswith(("参考文献", "内容来源")):
                    sources.append(text)
                elif title == "胎宝宝本周概况" and stats.match(text):
                    measurements.append(text)
                elif (
                    text not in labels
                    and not re.fullmatch(r"\d+月\d+日\s*[-—至]\s*\d+月\d+日", text)
                    and not text.startswith(("距离预产期", "宝宝留级了", "添加小组件", "你的每日定制食谱"))
                ):
                    paragraphs.append(text)
            # 文献通常位于推广内容之后，独立保留。
            sources.extend(text for text in section["内容"] if text.startswith("参考文献"))
            if paragraphs:
                sections.append({"title": title, "paragraphs": paragraphs})
        media = [
            {"title": WEEK_IMAGE_LABELS[Path(path).stem.split("-")[0]], "url": images[path]}
            for path in row["图片"]
        ]
        weeks[week] = {
            "week": week,
            "sections": sections,
            "measurements": unique(measurements),
            "sources": unique(sources),
            "images": media,
        }
    if set(weeks) != set(range(43)) or len(rows) != len(weeks):
        raise ValueError("周变化的孕周不连续或存在重复")
    return dict(sorted(weeks.items()))


def parse_checkup(row):
    """把准备、检查项目、参考费用、超声解读和问答按原素材分段。"""
    headings = {"本次重点", "产检准备", "产检项目", "美柚小贴士", "产检费用",
                "B超单解读", "产检单记录", "常见问题"}
    buckets, section = {}, []
    for entry in row["内容"]:
        kind, text = entry["控件"], clean(entry["文本"])
        if kind == "tv_title" and text in headings:
            section = buckets.setdefault(text, [])
        elif text and kind in {"tv_title", "tv_content", "tv_name", "tv_project", "tv_price"}:
            section.append((kind, text))

    def paragraphs(name):
        return unique(text for kind, text in buckets.get(name, []) if kind == "tv_content")

    def entries(name):
        grouped = {}
        for kind, text in buckets.get(name, []):
            if kind == "tv_title":
                group = grouped.setdefault(text, [])
            elif kind in {"tv_content", "tv_name"} and grouped:
                group.append(text)
        return [
            {"title": title, "description": "\n".join(unique(values)) or None}
            for title, values in grouped.items()
        ]

    fees = {}
    project = None
    for kind, text in buckets.get("产检费用", []):
        if kind == "tv_project":
            project = text
        elif kind == "tv_price" and project:
            fees[project] = text
            project = None

    return {
        "focus": "\n".join(paragraphs("本次重点")),
        "items": entries("产检项目"),
        "preparations": [{"title": text[:200], "description": text if len(text) > 200 else None}
                         for text in paragraphs("产检准备")],
        "tips": paragraphs("美柚小贴士"),
        "estimatedCosts": [{"title": title, "description": price} for title, price in fees.items()],
        "ultrasoundNotes": entries("B超单解读"),
        "questions": entries("常见问题"),
    }


def seed_content(checkups, bag_rows, images, previous):
    old_checkups = {row["title"]: row for row in previous["checkups"]}
    old_items = {(row["category"], row["name"]): row for row in previous["bag"]["items"]}
    seeds = []
    for index, row in enumerate(checkups, 1):
        title = row["孕周"]
        window = re.fullmatch(r"孕(\d+)(?:-(\d+))?周", title)
        first, last = int(window[1]), int(window[2] or window[1])
        seed_key = old_checkups.get(title, {}).get("seedKey", f"checkup-{index:02d}-w{first}-{last}")
        seeds.append({
            "seedKey": seed_key,
            "title": title,
            "suggestStartDay": first * 7,
            "suggestEndDay": last * 7 + 6,
            **parse_checkup(row),
            "imageUrl": images[row["图片"][0]],
        })
    bag = previous["bag"]
    items = []
    intros = {"BABY": {}, "MOTHER": {}}
    for index, row in enumerate(bag_rows, 1):
        name = row["物品"]
        category = {"宝宝物品": "BABY", "妈妈物品": "MOTHER"}[row["分组"]]
        quantity = re.fullmatch(r"(\d+(?:\.\d+)?)\s*(.*)", row["清单数量"])
        details = {d["控件"]: clean(d["文本"]) for d in row["详情"]}
        intros[category][name] = details["tvGoodDetialDescrible"]
        items.append({
            "seedKey": old_items.get((category, name), {}).get("seedKey", f"bag-item-{index:02d}"),
            "category": category,
            "name": name,
            "quantity": quantity[1],
            "unit": quantity[2],
            "purpose": clean(row["清单简述"]),
            "imageUrl": images[row["图片"][0]],
        })
    for rows in (seeds, items):
        if len({row["seedKey"] for row in rows}) != len(rows):
            raise ValueError("初始化稳定键重复")
    return {"checkups": seeds, "bag": {**bag, "introductions": intros, "items": items}}


def food_rows(groups):
    return [row for group in groups.values() for row in group["食物"]]


def foods(rows, images):
    result = []
    for row in rows:
        name = clean(row["食物"])
        media = {Path(path).stem.split("-")[0]: images[path] for path in row["图片"]}
        food = {
            "id": hashlib.sha256(name.encode()).hexdigest()[:20],
            "name": name,
            "rating": FOOD_RATINGS[row["孕期评级"]],
            "tags": unique(row["列表标签"].split("·")),
            "thumbnailUrl": media["列表缩略图"],
        }
        if details := row.get("详情"):
            food.update({
                "imageUrl": media["详情图片"],
                "aliases": clean(details.get("别名", "")).removeprefix("别名："),
                "summary": clean(details.get("简介", "")),
                "selectionTips": clean(details.get("选购攻略", "")),
                "source": clean(details["内容来源"]),
                "stageAdvice": [{"stage": r["适用阶段"], "text": clean(r["说明"])} for r in details["各阶段食用提示"]],
                "nutritionBasis": clean(details.get("营养计量基准", "")),
                "nutrition": [{"name": r["成分"], "value": r["含量"]} for r in details["热量和营养成分"]],
                "notes": unique(details.get("其他正文", [])),
            })
            if details.get("榜单"):
                food["ranking"] = details["榜单"]
            food["tags"] = unique([*food["tags"], *details.get("食物标签", "").split("·")])
        result.append(food)
    if len({r["id"] for r in result}) != len(result):
        raise ValueError("食物名称重复")
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path)
    source = parser.parse_args().source
    sources = {p.stem: read_json(p) for p in source.glob("*.json")}
    previous = read_json(RESOURCE_DIR / "seed-content.json")
    images = import_images(source, rows_with_images(sources))
    weeks = weekly(sources["孕期周变化"], images)
    growth = {
        "motherDaily": daily(sources["准妈妈每天变化"]),
        "babyDaily": daily(sources["胎宝宝每天变化"]),
        "weeks": weeks,
    }
    seed = seed_content(sources["产检时间表"], sources["待产包"], images, previous)
    catalog = foods(food_rows(sources["能不能吃"]) + food_rows(sources["能不能吃_仅列表"]), images)
    write_json("growth-content.json", growth)
    write_json("seed-content.json", seed)
    write_json("food-content.json", catalog)
    print(f"{len(weeks)} 周，{len(catalog)} 个食物，{len(images)} 张原始图对应 {len(set(images.values()))} 张资源图")


if __name__ == "__main__":
    main()
