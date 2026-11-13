package com.howe.meal.domain.dto;

import com.howe.meal.domain.MealCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 分类保存结果
 *
 * <p>分类新增/修改统一先经过 AI 审阅，因此响应同时携带审阅结论与实际保存值：
 * </p>
 * <ul>
 *   <li>{@code APPROVED}：AI 通过，已保存润色后的值；{@code changes} 列出被调整的字段；</li>
 *   <li>{@code REJECTED}：AI 拒绝，未保存；{@code reason} 与 {@code examples} 给出原因与修改示例；</li>
 *   <li>{@code SKIPPED}：AI 技术不可用，本地校验通过后按原值保存。</li>
 * </ul>
 *
 * @author howe
 */
@Data
@Schema(description = "点餐分类保存结果（含 AI 审阅结论）")
public class MealCategorySaveResult {

    /** 审阅状态：通过 */
    public static final String REVIEW_APPROVED = "APPROVED";
    /** 审阅状态：拒绝 */
    public static final String REVIEW_REJECTED = "REJECTED";
    /** 审阅状态：跳过（AI 不可用） */
    public static final String REVIEW_SKIPPED = "SKIPPED";

    @Schema(description = "AI 是否通过；跳过审阅时为 null")
    private Boolean approved;

    @Schema(description = "是否已落库")
    private Boolean saved;

    @Schema(description = "审阅状态：APPROVED / REJECTED / SKIPPED")
    private String reviewStatus;

    @Schema(description = "拒绝原因；通过或跳过时为空")
    private String reason;

    @Schema(description = "修改示例（最多 3 条）；仅在拒绝时返回")
    private List<String> examples = new ArrayList<>();

    @Schema(description = "实际保存（或将被保存）的分类")
    private MealCategory category;

    @Schema(description = "被 AI 调整的字段差异（原值 → 保存值）")
    private List<FieldChange> changes = new ArrayList<>();

    /**
     * 字段差异
     *
     * @param field  字段名
     * @param before 原值
     * @param after  保存值
     * @author howe
     */
    public record FieldChange(String field, String before, String after) {
    }
}
