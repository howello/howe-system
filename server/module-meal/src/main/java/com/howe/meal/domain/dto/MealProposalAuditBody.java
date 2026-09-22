package com.howe.meal.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 新菜提案审核入参
 *
 * @author howe
 */
@Data
@Schema(description = "新菜提案审核入参")
public class MealProposalAuditBody implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "提案ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "提案ID不能为空")
    private Long proposalId;

    @Schema(description = "审核结果（1通过 2驳回）", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "审核结果不能为空")
    private String status;

    @Schema(description = "审核意见", example = "已上架，周末做给你们吃")
    @Size(max = 500, message = "审核意见长度不能超过500个字符")
    private String auditRemark;
}
