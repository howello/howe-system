package com.howe.pregnancy.domain.vo;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 产检展示对象
 *
 * @author howe
 */
@Data
@Schema(description = "产检")
public class CheckupVO {

    @Schema(description = "产检ID")
    private String id;

    @Schema(description = "标题")
    private String title;

    @Schema(description = "建议窗口起始孕日")
    private Integer suggestStartDay;

    @Schema(description = "建议窗口结束孕日")
    private Integer suggestEndDay;

    @Schema(description = "手填建议日期")
    private String suggestedDate;

    @Schema(description = "派生的建议日期")
    private String derivedSuggestedDate;

    @Schema(description = "派生的建议窗口起始日期")
    private String derivedWindowStart;

    @Schema(description = "派生的建议窗口结束日期")
    private String derivedWindowEnd;

    @Schema(description = "实际预约日期")
    private String appointmentDate;

    @Schema(description = "实际预约时刻")
    private String appointmentTime;

    @Schema(description = "医院")
    private String hospital;

    @Schema(description = "地点")
    private String location;

    @Schema(description = "实际完成日期")
    private String completedDate;

    @Schema(description = "本次重点")
    private String focus;

    @Schema(description = "状态")
    private String status;

    @Schema(description = "是否逾期")
    private Boolean overdue;

    @Schema(description = "版本号")
    private Integer version;

    @Schema(description = "项目")
    private List<DetailVO> items;

    @Schema(description = "准备事项")
    private List<DetailVO> preparations;

    @Schema(description = "关联报告")
    private List<ReportVO> reports;

    @Schema(description = "费用明细")
    private List<ExpenseVO> expenses;

    @Schema(description = "内置的产检参考资料")
    private ReferenceVO reference;

    /** 随包发布的产检配图、标准项目、费用参考、超声解读与常见问题。 */
    public record ReferenceVO(String imageUrl, JsonNode items, JsonNode tips, JsonNode estimatedCosts,
                              JsonNode ultrasoundNotes, JsonNode questions) {}

    /**
     * 详情项
     */
    @Data
    @Schema(description = "产检详情项")
    public static class DetailVO {

        @Schema(description = "详情ID")
        private String id;

        @Schema(description = "标题")
        private String title;

        @Schema(description = "说明")
        private String description;

        @Schema(description = "排序")
        private Integer sortOrder;

        @Schema(description = "项目预计耗时（分钟）")
        private Integer estimatedMinutes;

        @Schema(description = "准备事项是否完成")
        private Boolean done;
    }
}
