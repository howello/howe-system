package com.howe.pregnancy.domain;

import com.howe.common.core.domain.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.util.List;

/**
 * 检查报告 preg_report
 *
 * <p>同日可有多份报告，一份报告可有多张有序图片。删除报告只解除测量关联与文件绑定，
 * 不删除测量与产检。</p>
 *
 * @author howe
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "检查报告")
public class PregReport extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @Schema(description = "报告ID")
    private Long id;

    @Schema(description = "报告名称")
    private String name;

    @Schema(description = "报告日期")
    private LocalDate reportDate;

    @Schema(description = "关联产检ID")
    private Long checkupId;

    @Schema(description = "备注")
    private String note;

    @Schema(description = "操作人账号ID")
    private Long actorUserId;

    @Schema(description = "客户端请求ID")
    private String requestId;

    @Schema(description = "请求内容摘要")
    private String requestHash;

    @Schema(description = "版本号")
    private Integer version;
    @Schema(description = "删除标记（0存在 2删除）")
    private String delFlag;


    @Schema(description = "图片元数据（详情查询填充）")
    private List<PregFile> files;

    @Schema(description = "费用明细（详情查询填充）")
    private List<PregExpense> expenses;
}
