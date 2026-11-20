package com.howe.pregnancy.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.howe.pregnancy.util.NumericTextSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 测量记录展示对象
 *
 * <p>ID 以字符串返回；数值与金额以十进制字符串返回，避免前端浮点误差。</p>
 *
 * @author howe
 */
@Data
@Schema(description = "测量记录")
public class MeasurementVO {

    @Schema(description = "记录ID")
    private String id;

    @Schema(description = "指标编码")
    private String metricCode;

    @Schema(description = "指标名称")
    private String metricName;

    @Schema(description = "发生时刻（带偏移 ISO 8601）")
    private String occurredAt;

    @Schema(description = "业务自然日")
    private String recordDate;

    @Schema(description = "单位")
    private String unit;

    @Schema(description = "场景编码")
    private String sceneCode;

    @Schema(description = "场景标签")
    private String sceneLabel;

    @Schema(description = "主值")
    @JsonSerialize(using = NumericTextSerializer.class)
    private BigDecimal numericValue;

    @Schema(description = "次值")
    @JsonSerialize(using = NumericTextSerializer.class)
    private BigDecimal secondaryValue;

    @Schema(description = "受控补充字段")
    private Map<String, Object> detail;

    @Schema(description = "备注")
    private String note;

    @Schema(description = "关联报告ID")
    private String reportId;

    @Schema(description = "记录人账号ID")
    private String actorUserId;

    @Schema(description = "记录人昵称")
    private String actorNickName;

    @Schema(description = "版本号")
    private Integer version;
}
