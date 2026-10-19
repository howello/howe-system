package com.howe.meal.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 菜品 AI 更新请求
 *
 * <p>用户在编辑页粘贴这道菜的完整做法，后端据此提取简介、标签、耗时、难度、份量、
 * 热量、用料、做法与小贴士，并直接覆盖写入数据库。</p>
 *
 * @author howe
 */
@Data
@Schema(description = "菜品 AI 更新请求（从完整做法提取菜品文本字段）")
public class DishAiUpdateRequest {

    @Schema(description = "菜品ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long dishId;

    @Schema(description = "这道菜的完整做法文本", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "五花肉切块焯水，冰糖炒糖色，加生抽老抽炖 40 分钟……")
    private String text;
}
