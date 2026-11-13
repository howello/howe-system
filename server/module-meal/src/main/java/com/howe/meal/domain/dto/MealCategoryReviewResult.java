package com.howe.meal.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 分类 AI 审阅的结构化输出
 *
 * <p>与 AI 场景 {@code meal.category.review} 的 JSON Schema 一一对应。
 * 模型返回后还会经过本地类型、长度、公共名称锁定、重名与归属校验，
 * 不直接信任模型输出。</p>
 *
 * @author howe
 */
@Data
@Schema(description = "分类 AI 审阅结果")
public class MealCategoryReviewResult {

    @Schema(description = "AI 是否通过")
    private Boolean approved;

    @Schema(description = "拒绝原因；通过时为空")
    private String reason;

    @Schema(description = "润色后的分类名称")
    private String name;

    @Schema(description = "润色后的分类说明")
    private String description;

    @Schema(description = "润色后的分类依据")
    private String basis;

    @Schema(description = "分类图标（原样回显，不由 AI 生成）")
    private String icon;

    @Schema(description = "修改示例（拒绝时返回，最多 3 条）")
    private List<String> examples = new ArrayList<>();
}
