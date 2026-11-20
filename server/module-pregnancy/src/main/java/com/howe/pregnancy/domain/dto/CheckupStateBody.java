package com.howe.pregnancy.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 产检状态动作请求
 *
 * @author howe
 */
@Data
@Schema(description = "产检状态动作请求")
public class CheckupStateBody {

    @Schema(description = "实际完成日期（完成动作必填，不得晚于业务今天）")
    private String completedDate;

    @Schema(description = "父版本")
    @NotNull(message = "版本号不能为空")
    private Integer version;
}
