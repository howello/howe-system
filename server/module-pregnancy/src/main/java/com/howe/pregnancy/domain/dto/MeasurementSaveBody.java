package com.howe.pregnancy.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 测量记录新增/编辑请求
 *
 * <p>指标编码在编辑时不允许修改；补充字段以 {@code detail} 对象传递，
 * 按固定指标定义校验。</p>
 *
 * @author howe
 */
@Data
@Schema(description = "测量记录保存请求")
public class MeasurementSaveBody {

    @Schema(description = "客户端请求ID，新增时必填", example = "measure-example-1")
    @Size(max = 64, message = "请求ID长度不能超过64")
    private String requestId;

    @Schema(description = "固定指标编码", example = "blood_pressure")
    @NotBlank(message = "指标编码不能为空")
    private String metricCode;

    @Schema(description = "测量发生时刻（带偏移 ISO 8601）", example = "2026-11-17T07:20:00+08:00")
    @NotBlank(message = "测量时间不能为空")
    private String occurredAt;

    @Schema(description = "单位", example = "mmHg")
    private String unit;

    @Schema(description = "场景编码", example = "FASTING")
    private String scene;

    @Schema(description = "主数值，以字符串传递", example = "112")
    private BigDecimal numericValue;

    @Schema(description = "次值，以字符串传递", example = "72")
    private BigDecimal secondaryValue;

    @Schema(description = "受控补充字段")
    private Map<String, Object> detail;

    @Schema(description = "备注")
    @Size(max = 2000, message = "备注长度不能超过2000")
    private String note;

    @Schema(description = "关联报告ID")
    private String reportId;

    @Schema(description = "版本号，编辑时必填")
    private Integer version;
}
