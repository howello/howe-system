package com.howe.blog.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 首页友链与订阅同步概况
 *
 * <p>订阅源即 {@code blog_link.link_type='2'} 的记录，同步失败以
 * {@code last_error} 非空判定，与博客朋友圈管理页口径一致。</p>
 *
 * @param feedTotal      订阅源总数
 * @param failedCount    同步失败数（last_error 非空）
 * @param lastSyncTime   最近一次同步时间（yyyy-MM-dd HH:mm）；从未同步时为 null
 * @param failures       失败源明细（最多 3 条）
 * @author howe
 */
@Schema(description = "首页友链与订阅同步概况")
public record BlogLinkSyncOverview(
        @Schema(description = "订阅源总数") long feedTotal,
        @Schema(description = "同步失败数") long failedCount,
        @Schema(description = "最近一次同步时间") String lastSyncTime,
        @Schema(description = "失败源明细") List<FeedFailure> failures) {

    /**
     * 同步失败条目
     *
     * @param linkName  站点名称
     * @param lastError 失败原因
     * @author howe
     */
    @Schema(description = "同步失败条目")
    public record FeedFailure(
            @Schema(description = "站点名称") String linkName,
            @Schema(description = "失败原因") String lastError) {
    }
}
