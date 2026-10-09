package com.howe.meal.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 菜品一键 AI 生成结果
 *
 * <p>只包含被补齐的字段，前端据此回填表单；生成结果不落库。</p>
 *
 * @author howe
 */
@Data
@Schema(description = "菜品一键 AI 生成结果（只含被补齐的字段）")
public class DishAiGenerateResult {

    @Schema(description = "本次生成的文本字段集合，不含用户已填字段")
    private Map<String, Object> fields;

    @Schema(description = "新生成的封面永久地址（仅当缺失且生成成功）")
    private String cover;

    @Schema(description = "从模型返回的分类名中匹配到的分类ID")
    private List<Long> matchedCategoryIds;

    @Schema(description = "未匹配到现有分类的分类名（仅提示，不落库）")
    private List<String> unmatchedCategoryNames;

    @Schema(description = "图片未生成时的可读原因")
    private String imageError;
}
