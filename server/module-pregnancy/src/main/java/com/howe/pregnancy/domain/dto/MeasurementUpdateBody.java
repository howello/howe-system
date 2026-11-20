package com.howe.pregnancy.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 测量记录编辑请求
 *
 * <p>PATCH 语义：未出现的字段保持原值；指标编码不允许修改，因此不在编辑请求中。
 * 必填字段（测量时间、数值等）为空表示不改，由服务端沿用原值后统一校验。</p>
 *
 * @author howe
 */
@Data
@Schema(description = "测量记录编辑请求")
public class MeasurementUpdateBody {

    @Schema(description = "版本号，必填")
    private Integer version;

    @Schema(description = "测量发生时刻（带偏移 ISO 8601）")
    private String occurredAt;

    @Schema(description = "单位")
    private String unit;

    @Schema(description = "场景编码")
    private String scene;

    @Schema(description = "主数值，以字符串传递")
    private BigDecimal numericValue;

    @Schema(description = "次值，以字符串传递")
    private BigDecimal secondaryValue;

    @Schema(description = "受控补充字段")
    private Map<String, Object> detail;

    @Schema(description = "备注")
    private String note;

    @Schema(description = "关联报告ID")
    private String reportId;
}
