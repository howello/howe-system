package com.howe.pregnancy.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 费用保存请求
 *
 * @author howe
 */
@Data
@Schema(description = "费用保存请求")
public class ExpenseSaveBody {

    @Schema(description = "客户端请求ID，新增时必填")
    @Size(max = 64, message = "请求ID长度不能超过64")
    private String requestId;

    @Schema(description = "费用分类")
    @NotBlank(message = "费用分类不能为空")
    private String categoryCode;

    @Schema(description = "费用名称")
    @Size(max = 200, message = "费用名称长度不能超过200")
    private String label;

    @Schema(description = "实付金额，以字符串传递")
    @NotBlank(message = "金额不能为空")
    private String amount;

    @Schema(description = "实际支出日期，默认业务今天")
    private String spentDate;

    @Schema(description = "版本号，编辑时必填")
    private Integer version;

    @Schema(description = "父资源版本，父资源存在时必填")
    private Integer parentVersion;
}
