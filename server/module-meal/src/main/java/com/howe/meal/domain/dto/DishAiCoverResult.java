package com.howe.meal.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 菜品一键 AI 封面生成结果
 *
 * <p>只包含封面地址或失败原因；生成结果不落库。</p>
 *
 * @author howe
 */
@Data
@Schema(description = "菜品一键 AI 封面生成结果")
public class DishAiCoverResult {

    @Schema(description = "新生成的封面永久地址（仅当缺失且生成成功）")
    private String cover;

    @Schema(description = "图片未生成时的可读原因")
    private String imageError;
}
