package com.howe.meal.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 新菜提案 AI 自动审核结果
 *
 * <p>一次结构化调用同时返回「是否通过」与「补齐的字段」：通过时 {@link #text} 承载补齐结果，
 * 驳回时 {@link #rejectReason} 承载可读原因。</p>
 *
 * @author howe
 */
@Data
@Schema(description = "新菜提案 AI 自动审核结果")
public class DishAiAuditResult {

    @Schema(description = "是否审核通过", example = "true")
    private boolean approved = true;

    @Schema(description = "驳回原因（未通过时给出）", example = "菜名不是一道可制作的家常菜品")
    private String rejectReason;

    @Schema(description = "补齐的字段与分类匹配结果（通过时有效）")
    private DishAiTextResult text = new DishAiTextResult();
}
