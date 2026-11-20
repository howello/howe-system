package com.howe.pregnancy.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 检查报告展示对象
 *
 * @author howe
 */
@Data
@Schema(description = "检查报告")
public class ReportVO {

    @Schema(description = "报告ID")
    private String id;

    @Schema(description = "报告名称")
    private String name;

    @Schema(description = "报告日期")
    private String reportDate;

    @Schema(description = "关联产检ID")
    private String checkupId;

    @Schema(description = "备注")
    private String note;

    @Schema(description = "操作人账号ID")
    private String actorUserId;

    @Schema(description = "版本号")
    private Integer version;

    @Schema(description = "有序图片")
    private List<FileVO> files;

    @Schema(description = "关联的人工测量")
    private List<MeasurementVO> measurements;

    @Schema(description = "费用明细")
    private List<ExpenseVO> expenses;
}
