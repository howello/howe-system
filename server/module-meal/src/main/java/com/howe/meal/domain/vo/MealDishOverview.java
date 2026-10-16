package com.howe.meal.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 首页点餐-菜品概况
 *
 * <p>统计口径与菜品列表一致：公共菜谱（{@code dept_id = 0}）对所有家庭可见，
 * 家庭私有菜只统计本家庭；平台管理员统计全量。</p>
 *
 * @param onShelfCount  上架菜品数（status='0'）
 * @param offShelfCount 下架菜品数（status='1'）
 * @param categoryCount 分类数
 * @param publicCount   公共菜谱数（dept_id=0）
 * @param familyCount   家庭菜谱数（dept_id<>0）
 * @author howe
 */
@Schema(description = "首页点餐-菜品概况")
public record MealDishOverview(
        @Schema(description = "上架菜品数") long onShelfCount,
        @Schema(description = "下架菜品数") long offShelfCount,
        @Schema(description = "分类数") long categoryCount,
        @Schema(description = "公共菜谱数") long publicCount,
        @Schema(description = "家庭菜谱数") long familyCount) {
}
