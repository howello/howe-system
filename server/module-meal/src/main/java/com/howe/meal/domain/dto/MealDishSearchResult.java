package com.howe.meal.domain.dto;

import com.howe.meal.domain.MealDish;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 点餐菜品搜索结果。
 *
 * @param query 规范化后的用户查询
 * @param source 结果来源：local 表示数据库直接命中，ai 表示经 AI 扩展词项后命中
 * @param items 当前用户可见的真实菜品候选
 */
@Schema(description = "点餐菜品搜索结果")
public record MealDishSearchResult(String query, String source, List<MealDish> items) {
}
