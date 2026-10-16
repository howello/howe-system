package com.howe.share.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 首页分享缓存健康
 *
 * <p>缓存状态取自 {@code share_link.cache_status}：0 待缓存 / 1 缓存中 / 2 就绪 / 3 失败。
 * 状态名与分享管理页保持一致，便于对照排查。</p>
 *
 * @param readyCount   已就绪数（cache_status='2'）
 * @param pendingCount 待缓存数（cache_status='0'）
 * @param failedCount  缓存失败数（cache_status='3'）
 * @author howe
 */
@Schema(description = "首页分享缓存健康")
public record ShareCacheHealth(
        @Schema(description = "已就绪数") long readyCount,
        @Schema(description = "待缓存数") long pendingCount,
        @Schema(description = "缓存失败数") long failedCount) {
}
