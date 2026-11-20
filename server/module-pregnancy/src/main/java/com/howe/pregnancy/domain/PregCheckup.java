package com.howe.pregnancy.domain;

import com.howe.common.core.domain.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * 产检计划 preg_checkup
 *
 * <p>建议窗口按孕日保存，实际预约与完成日期单独保存；「逾期」是查询派生标签，
 * 不额外维护手动状态。</p>
 *
 * @author howe
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "产检计划")
public class PregCheckup extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @Schema(description = "产检ID")
    private Long id;

    @Schema(description = "代码初始清单稳定键")
    private String seedKey;

    @Schema(description = "标题")
    private String title;

    @Schema(description = "建议窗口起始孕日")
    private Integer suggestStartDay;

    @Schema(description = "建议窗口结束孕日")
    private Integer suggestEndDay;

    @Schema(description = "手填建议日期")
    private LocalDate suggestedDate;

    @Schema(description = "实际预约日期")
    private LocalDate appointmentDate;

    @Schema(description = "实际预约时刻")
    private LocalTime appointmentTime;

    @Schema(description = "医院")
    private String hospital;

    @Schema(description = "地点")
    private String location;

    @Schema(description = "实际完成日期")
    private LocalDate completedDate;

    @Schema(description = "本次重点说明")
    private String focus;

    @Schema(description = "状态")
    private String status;

    @Schema(description = "客户端请求ID")
    private String requestId;

    @Schema(description = "请求内容摘要")
    private String requestHash;

    @Schema(description = "版本号")
    private Integer version;
    @Schema(description = "删除标记（0存在 2删除）")
    private String delFlag;


    @Schema(description = "项目列表（详情查询填充）")
    private List<PregCheckupDetail> items;

    @Schema(description = "准备事项列表（详情查询填充）")
    private List<PregCheckupDetail> preparations;

    @Schema(description = "关联报告（详情查询填充）")
    private List<PregReport> reports;

    @Schema(description = "费用明细（详情查询填充）")
    private List<PregExpense> expenses;

    @Schema(description = "派生的建议日期")
    private LocalDate derivedSuggestedDate;

    @Schema(description = "派生的建议起始日期")
    private LocalDate derivedWindowStart;

    @Schema(description = "派生的建议结束日期")
    private LocalDate derivedWindowEnd;

    @Schema(description = "是否逾期")
    private Boolean overdue;
}
