package com.howe.pregnancy.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 导出申请请求
 *
 * @author howe
 */
@Data
@Schema(description = "导出申请请求")
public class ExportCreateBody {

    @Schema(description = "客户端请求ID")
    @Size(max = 64, message = "请求ID长度不能超过64")
    private String requestId;

    @Schema(description = "数据格式（CSV/XLSX）")
    @NotBlank(message = "导出格式不能为空")
    private String format;

    @Schema(description = "是否包含健康记录")
    private Boolean includeRecords;

    @Schema(description = "是否包含产检与费用")
    private Boolean includeCheckupsAndExpenses;

    @Schema(description = "是否包含报告原图")
    private Boolean includeReportPhotos;

    @Schema(description = "是否包含其他原图（饮食照片）")
    private Boolean includeOtherPhotos;
}
