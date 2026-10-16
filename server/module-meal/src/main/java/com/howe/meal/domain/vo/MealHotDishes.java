package com.howe.meal.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 首页点餐-热门菜品
 *
 * <p>口径：近 30 天内已完成订单（{@code status='2'}）的菜品明细按份数汇总，
 * 取 Top 5。数据范围遵循点餐模块既有的本部门隔离。</p>
 *
 * @param dishes 按份数倒序的 Top 5；无数据时为空列表
 * @author howe
 */
@Schema(description = "首页点餐-热门菜品")
public record MealHotDishes(
        @Schema(description = "按份数倒序的 Top 5；无数据时为空列表") List<HotDish> dishes) {

    /**
     * 热门菜品条目
     *
     * @param dishName 菜名快照（取自订单明细，菜品改名不影响历史统计）
     * @param count    近 30 天被点份数
     * @author howe
     */
    @Schema(description = "热门菜品条目")
    public record HotDish(
            @Schema(description = "菜名") String dishName,
            @Schema(description = "近 30 天被点份数") long count) {
    }
}
