package com.howe.meal.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 厨师工作台待办订单汇总
 *
 * <p>只统计仍在流转中的订单：待接单与制作中。统计范围与厨师工作台列表一致，
 * 由 {@code @DataScope} 按当前登录用户的家庭数据权限隔离。</p>
 *
 * @param pendingCount 待接单订单数
 * @param cookingCount 制作中订单数
 * @author howe
 */
@Schema(description = "厨师工作台待办订单汇总")
public record MealKitchenOrderSummary(
        @Schema(description = "待接单订单数") long pendingCount,
        @Schema(description = "制作中订单数") long cookingCount) {
}
