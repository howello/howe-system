package com.howe.blog.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 首页博客聚合统计（含降级标志）
 *
 * @param blogAvailable 博客表是否可用；false 表示 blog 表缺失，blog 为 null
 * @param blog          聚合统计块；blogAvailable 为 false 时为 null
 * @author howe
 */
@Schema(description = "首页博客聚合统计")
public record BlogHomeStats(
        @Schema(description = "博客模块是否可用（blog 表存在且查询成功）") boolean blogAvailable,
        @Schema(description = "聚合统计块，blog 表缺失时为 null") BlogStats blog) {
}