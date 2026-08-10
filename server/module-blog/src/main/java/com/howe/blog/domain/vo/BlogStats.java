package com.howe.blog.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 博客聚合统计块
 *
 * @param articleTotal   文章总数（管理视角含隐藏）
 * @param draftTotal     草稿总数（仅 status=0）
 * @param talkTotal      说说总数
 * @param today          今日三个计数（由 30 天窗口今天桶得出，不额外查询）
 * @param trend          近 30 天逐日计数，缺日补 0、升序、固定 30 元素
 * @param categoryCounts 分类分布（Top7 + 其他），空分类不计入
 * @author howe
 */
@Schema(description = "博客聚合统计块")
public record BlogStats(
        @Schema(description = "文章总数（管理视角含隐藏）") long articleTotal,
        @Schema(description = "草稿总数（仅 status=0）") long draftTotal,
        @Schema(description = "说说总数") long talkTotal,
        @Schema(description = "今日新增计数") BlogTodayStats today,
        @Schema(description = "近 30 天逐日计数，固定 30 元素，升序") List<BlogTrendPoint> trend,
        @Schema(description = "分类分布（Top7 + 其他）") List<BlogCategoryCount> categoryCounts) {
}