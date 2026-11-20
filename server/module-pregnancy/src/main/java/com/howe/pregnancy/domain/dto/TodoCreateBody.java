package com.howe.pregnancy.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 待办新增请求
 *
 * @author howe
 */
@Data
@Schema(description = "待办新增请求")
public class TodoCreateBody {

    @Schema(description = "客户端请求ID")
    @Size(max = 64, message = "请求ID长度不能超过64")
    private String requestId;

    @Schema(description = "标题")
    @NotBlank(message = "待办标题不能为空")
    @Size(max = 200, message = "标题长度不能超过200")
    private String title;

    @Schema(description = "日期（允许未来）")
    @NotBlank(message = "待办日期不能为空")
    private String dueDate;

    @Schema(description = "可选时刻（HH:mm）")
    private String dueTime;

    @Schema(description = "备注")
    @Size(max = 2000, message = "备注长度不能超过2000")
    private String note;
}
