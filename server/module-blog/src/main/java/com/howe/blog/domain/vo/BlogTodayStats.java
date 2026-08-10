package com.howe.blog.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 今日新增计数
 *
 * @param articles 今日新增文章数
 * @param drafts   今日新增草稿数
 * @param talks    今日新增说说数
 * @author howe
 */
@Schema(description = "今日新增计数")
public record BlogTodayStats(
        @Schema(description = "今日新增文章数") long articles,
        @Schema(description = "今日新增草稿数") long drafts,
        @Schema(description = "今日新增说说数") long talks) {
}