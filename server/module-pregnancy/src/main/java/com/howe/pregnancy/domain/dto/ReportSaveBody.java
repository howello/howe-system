package com.howe.pregnancy.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 报告保存请求
 *
 * @author howe
 */
@Data
@Schema(description = "检查报告保存请求")
public class ReportSaveBody {

    @Schema(description = "客户端请求ID，新增时必填")
    @Size(max = 64, message = "请求ID长度不能超过64")
    private String requestId;

    @Schema(description = "报告名称")
    @NotBlank(message = "报告名称不能为空")
    @Size(max = 200, message = "报告名称长度不能超过200")
    private String name;

    @Schema(description = "报告日期（不得晚于业务今天）")
    @NotBlank(message = "报告日期不能为空")
    private String reportDate;

    @Schema(description = "关联产检ID，可为空")
    private String checkupId;

    @Schema(description = "备注")
    @Size(max = 2000, message = "备注长度不能超过2000")
    private String note;

    @Schema(description = "文件ID列表，顺序即展示顺序")
    private List<String> fileIds;

    @Schema(description = "版本号，编辑时必填")
    private Integer version;
}
