package com.howe.share.route;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 分享路由（Redis 只读缓存内容）
 *
 * <p>只缓存「字节落在哪」这类在缓存就绪后不再变化的字段，用于加速会话内多次读取。
 * 链接状态与查看次数始终以 MySQL 为准，不放进缓存，避免破坏阅后即焚语义。</p>
 *
 * @param linkId          分享ID
 * @param storageType     存储位置（0本地 1R2）
 * @param storageLocation 存储地址
 * @param contentType     MIME 类型
 * @param fileName        文件名
 * @param fileSize        字节数
 * @author howe
 */
@Schema(description = "分享路由缓存")
public record ShareRoute(
        Long linkId,
        String storageType,
        String storageLocation,
        String contentType,
        String fileName,
        Long fileSize) {
}
