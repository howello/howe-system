package com.howe.meal.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * App 版本更新日志润色请求
 *
 * <p>供 CI 发版脚本调用：把本次发布的提交标题交给 AI，提炼成家庭用户能看懂的更新说明。
 * 润色结果不落库，由调用方随后通过发布接口作为 {@code updateLog} 一并提交。</p>
 *
 * @author howe
 */
@Data
@Schema(description = "App 版本更新日志润色请求")
public class AppVersionLogPolishBody {

    @Schema(description = "版本名称", example = "1.0.15")
    private String versionName;

    @Schema(description = "版本序号", example = "118")
    private Integer versionCode;

    @Schema(description = "原始提交摘要，每行一条",
            requiredMode = Schema.RequiredMode.REQUIRED,
            example = "feat(menu): 菜品编辑页与厨房卡片封面\nfix: 修复下架菜品仍显示的问题")
    private String source;
}
