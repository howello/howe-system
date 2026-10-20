package com.howe.meal.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Map;

/**
 * 菜品一键 AI 文本补齐结果
 *
 * <p>只包含被补齐的文本字段与分类匹配结果，不含封面；生成结果不落库。
 * 分类是唯一主类，因此最多只有一个匹配结果。</p>
 *
 * @author howe
 */
@Data
@Schema(description = "菜品一键 AI 文本补齐结果（只含被补齐的文本字段）")
public class DishAiTextResult {

    @Schema(description = "本次生成的文本字段集合，不含用户已填字段")
    private Map<String, Object> fields;

    @Schema(description = "匹配到的唯一主类ID；未匹配时为 null")
    private Long matchedCategoryId;

    @Schema(description = "模型返回但不在 9 个主类内的名称（仅提示，不落库）")
    private String unmatchedCategoryName;
}
