package com.howe.meal.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 菜品一键 AI 生成请求
 *
 * <p>前端把「菜名 + 当前表单已填字段快照」传上来，后端只补齐快照里为空的字段，
 * 已填字段不请求、不覆盖。</p>
 *
 * @author howe
 */
@Data
@Schema(description = "菜品一键 AI 生成请求")
public class DishAiGenerateRequest {

    @Schema(description = "菜名", requiredMode = Schema.RequiredMode.REQUIRED, example = "锅包肉")
    private String name;

    @Schema(description = "归属家庭ID（仅管理员可指定；0=公共菜谱，留空取当前家庭）")
    private Long deptId;

    @Schema(description = "当前表单已填字段快照，用于只补缺失")
    private Current current;

    /**
     * 当前表单已填字段快照
     *
     * <p>字段名与菜品字段一致，为空的字段会被补齐，非空的保持不变。</p>
     */
    @Data
    @Schema(description = "当前表单已填字段快照")
    public static class Current {

        @Schema(description = "已选分类ID列表")
        private List<Long> categoryIds;

        @Schema(description = "已填简介")
        private String description;

        @Schema(description = "已填封面图地址")
        private String cover;

        @Schema(description = "已填标签")
        private String tags;

        @Schema(description = "已填耗时")
        private String duration;

        @Schema(description = "已填难度")
        private String level;

        @Schema(description = "已填份量")
        private String serve;

        @Schema(description = "已填热量")
        private String kcal;

        @Schema(description = "已填用料 JSON")
        private String ingredients;

        @Schema(description = "已填做法 JSON")
        private String steps;

        @Schema(description = "已填小贴士 JSON")
        private String tips;
    }
}
