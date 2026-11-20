package com.howe.pregnancy.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 共享设置
 *
 * <p>单家庭、单次孕期：预产期与业务日期来自 {@code sys_config}，能力来自当前有效角色。
 * 数据读端不需要传业务归属 ID。</p>
 *
 * @author howe
 */
@Data
@Schema(description = "孕期共享设置")
public class SettingsVO {

    @Schema(description = "预产期（YYYY-MM-DD），未配置为空")
    private String dueDate;

    /** 末次月经日期 */
    private String lastMenstrualPeriod;

    @Schema(description = "业务今天（YYYY-MM-DD）")
    private String businessToday;

    @Schema(description = "业务时区")
    private String timezone;

    @Schema(description = "今日孕周展示文本，未配置预产期为空")
    private String gestationalAge;

    @Schema(description = "今日孕日，未配置预产期为空")
    private Integer gestationalDay;

    @Schema(description = "距预产期天数，未配置预产期为空")
    private Long daysUntilDue;

    @Schema(description = "距 37 周天数，未配置预产期为空")
    private Long daysUntil37Weeks;

    @Schema(description = "是否可写业务记录")
    private Boolean canWrite;

    @Schema(description = "是否可管理提醒规则")
    private Boolean canManageReminders;

    @Schema(description = "当前账号的孕期角色键")
    private List<String> roles;

    @Schema(description = "当前账号昵称，用于顶部展示")
    private String nickName;

    @Schema(description = "当前账号头像")
    private String avatar;
}
