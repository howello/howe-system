package com.howe.share.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 首页分享统计
 *
 * <p>访问与下载次数取自访问日志（{@code action='detail'/'open'/'file'} 计为访问，
 * {@code action='download'} 计为下载，仅统计 {@code result='success'}）。</p>
 *
 * @param linkTotal        链接总数（未删除）
 * @param todayViews       今日访问次数
 * @param todayDownloads   今日下载次数
 * @param recentLinks      最近创建的链接（最多 3 条，按创建时间倒序）
 * @author howe
 */
@Schema(description = "首页分享统计")
public record ShareHomeStats(
        @Schema(description = "链接总数") long linkTotal,
        @Schema(description = "今日访问次数") long todayViews,
        @Schema(description = "今日下载次数") long todayDownloads,
        @Schema(description = "最近创建的链接") List<RecentLink> recentLinks) {

    /**
     * 最近创建的链接条目
     *
     * @param fileName   文件名
     * @param createTime 创建时间（yyyy-MM-dd HH:mm）
     * @author howe
     */
    @Schema(description = "最近创建的链接条目")
    public record RecentLink(
            @Schema(description = "文件名") String fileName,
            @Schema(description = "创建时间 yyyy-MM-dd HH:mm") String createTime) {
    }
}
