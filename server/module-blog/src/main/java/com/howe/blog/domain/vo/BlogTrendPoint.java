package com.howe.blog.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 近 30 天趋势点
 *
 * @param date    日期 yyyy-MM-dd
 * @param articles 当日新增文章数
 * @param drafts  当日新增草稿数
 * @param talks   当日新增说说数
 * @author howe
 */
@Schema(description = "近 30 天趋势点")
public record BlogTrendPoint(
        @Schema(description = "日期 yyyy-MM-dd") String date,
        @Schema(description = "当日新增文章数") long articles,
        @Schema(description = "当日新增草稿数") long drafts,
        @Schema(description = "当日新增说说数") long talks) {
}