package com.howe.meal.domain.dto;

import com.howe.meal.domain.MealDish;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Map;

/**
 * 菜品 AI 更新结果
 *
 * <p>AI 提取出的非空字段会直接写入数据库；{@code fields} 记录本次实际写入的字段，
 * {@code dish} 返回更新后的完整菜品，供前端刷新表单。</p>
 *
 * @author howe
 */
@Data
@Schema(description = "菜品 AI 更新结果（本次写入的字段与更新后的菜品）")
public class DishAiUpdateResult {

    @Schema(description = "本次实际写入数据库的字段集合；未提取到的字段不在其中")
    private Map<String, Object> fields;

    @Schema(description = "更新后的完整菜品信息")
    private MealDish dish;
}
