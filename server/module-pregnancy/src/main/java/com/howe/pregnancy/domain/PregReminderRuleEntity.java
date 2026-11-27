package com.howe.pregnancy.domain;

import com.howe.common.core.domain.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 提醒规则 preg_reminder_rule
 *
 * <p>一行一条规则，编码与 {@code PregnancyReminderRule} 枚举一一对应。
 * 规则整体保存时以表级 {@code rule_version} 做乐观锁，避免并发覆盖。</p>
 *
 * @author howe
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "孕期提醒规则")
public class PregReminderRuleEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @Schema(description = "规则ID")
    private Long id;

    @Schema(description = "规则编码（与枚举一致）")
    private String ruleCode;

    @Schema(description = "是否启用")
    private Boolean enabled;

    @Schema(description = "提醒时刻（HH:mm）")
    private String remindTime;

    @Schema(description = "提前天数（产检提前提醒）")
    private Integer daysBefore;

    @Schema(description = "星期（0 周日，待产包周提醒）")
    private Integer weekday;

    @Schema(description = "规则版本（表级乐观锁）")
    private Integer ruleVersion;
}
