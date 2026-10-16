package com.howe.meal.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 首页点餐统计
 *
 * <p>数据范围遵循点餐模块既有的 {@code data_scope='3'}（本部门）隔离：
 * 家庭管理员与厨师只看到本家庭的订单与评价，平台管理员看到全部。</p>
 *
 * @param todayOrders   今日订单数
 * @param pendingCount  待接单数（status='0'）
 * @param cookingCount  制作中数（status='1'）
 * @param doneCount     已完成数（status='2'）
 * @param avgScore      评价平均分（1-5，保留一位小数）；无评价时为 0
 * @param trend         近 7 天订单量，缺日补 0、升序、固定 7 元素
 * @author howe
 */
@Schema(description = "首页点餐统计")
public record MealHomeStats(
        @Schema(description = "今日订单数") long todayOrders,
        @Schema(description = "待接单数") long pendingCount,
        @Schema(description = "制作中数") long cookingCount,
        @Schema(description = "已完成数") long doneCount,
        @Schema(description = "评价平均分；无评价时为 0") double avgScore,
        @Schema(description = "近 7 天订单量，固定 7 元素，升序") List<MealOrderTrendPoint> trend) {

    /**
     * 近 7 天订单趋势点
     *
     * @param date  日期 yyyy-MM-dd
     * @param count 当日订单数
     * @author howe
     */
    @Schema(description = "近 7 天订单趋势点")
    public record MealOrderTrendPoint(
            @Schema(description = "日期 yyyy-MM-dd") String date,
            @Schema(description = "当日订单数") long count) {
    }
}
