package com.howe.pregnancy.domain;

import com.howe.common.core.domain.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 固定指标测量 preg_measurement
 *
 * <p>一次真实测量对应一行：主值、次值与受控补充字段同表保存，不再分主表与值表。
 * 同一时刻允许存在多条真实测量，排序按发生时刻其次 ID。</p>
 *
 * @author howe
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "孕期测量记录")
public class PregMeasurement extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @Schema(description = "测量记录ID")
    private Long id;

    @Schema(description = "固定指标编码")
    private String metricCode;

    @Schema(description = "测量发生时刻")
    private OffsetDateTime occurredAt;

    @Schema(description = "业务自然日")
    private LocalDate recordDate;

    @Schema(description = "实际单位")
    private String unitCode;

    @Schema(description = "场景编码")
    private String sceneCode;

    @Schema(description = "主数值")
    private BigDecimal numericValue;

    @Schema(description = "次数值")
    private BigDecimal secondaryValue;

    @Schema(description = "受控补充字段 JSON")
    private String detailJson;

    @Schema(description = "备注")
    private String note;

    @Schema(description = "关联报告ID")
    private Long reportId;

    @Schema(description = "记录人账号ID")
    private Long actorUserId;

    @Schema(description = "记录人昵称（查询填充）")
    private String actorNickName;

    @Schema(description = "客户端请求ID")
    private String requestId;

    @Schema(description = "请求内容摘要")
    private String requestHash;

    @Schema(description = "版本号")
    private Integer version;
    @Schema(description = "删除标记（0存在 2删除）")
    private String delFlag;

}
