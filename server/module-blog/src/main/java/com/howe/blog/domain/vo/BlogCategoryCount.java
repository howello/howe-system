package com.howe.blog.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 分类分布项
 *
 * @param name  分类名；溢出统一的 '其他'
 * @param count 文章数
 * @author howe
 */
@Schema(description = "分类分布项")
public record BlogCategoryCount(
        @Schema(description = "分类名；溢出统一的 '其他'") String name,
        @Schema(description = "文章数") long count) {
}