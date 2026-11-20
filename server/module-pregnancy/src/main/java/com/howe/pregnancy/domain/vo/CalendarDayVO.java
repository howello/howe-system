package com.howe.pregnancy.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 日历当天详情
 *
 * @author howe
 */
@Data
@Schema(description = "日历当天详情")
public class CalendarDayVO {

    @Schema(description = "日期")
    private String date;

    @Schema(description = "业务今天")
    private String businessToday;

    @Schema(description = "孕周展示文本")
    private String gestationalAge;

    @Schema(description = "孕日")
    private Integer gestationalDay;

    @Schema(description = "记录分组（按指标聚合）")
    private List<RecordGroup> recordGroups;

    @Schema(description = "饮食记录")
    private List<DietItem> diets;

    @Schema(description = "当天的产检")
    private List<CheckupItem> checkups;

    @Schema(description = "当天的报告")
    private List<ReportItem> reports;

    @Schema(description = "当天的待办")
    private List<TodoItem> todos;

    @Schema(description = "饮水合计")
    private String waterTotal;

    @Schema(description = "允许的入口")
    private AllowedActions allowedActions;

    /**
     * 测量记录分组
     */
    @Data
    @Schema(description = "测量记录分组")
    public static class RecordGroup {

        @Schema(description = "指标编码")
        private String metricCode;

        @Schema(description = "指标名称")
        private String metricName;

        @Schema(description = "记录")
        private List<MeasurementVO> records;
    }

    /**
     * 饮食摘要
     */
    @Data
    @Schema(description = "饮食摘要")
    public static class DietItem {

        @Schema(description = "记录ID")
        private String id;

        @Schema(description = "餐次")
        private String mealType;

        @Schema(description = "发生时刻")
        private String occurredAt;

        @Schema(description = "内容")
        private String content;

        @Schema(description = "图片数量")
        private Integer fileCount;
    }

    /**
     * 产检摘要
     */
    @Data
    @Schema(description = "产检摘要")
    public static class CheckupItem {

        @Schema(description = "产检ID")
        private String id;

        @Schema(description = "标题")
        private String title;

        @Schema(description = "状态")
        private String status;

        @Schema(description = "预约时刻")
        private String appointmentTime;

        @Schema(description = "医院")
        private String hospital;
    }

    /**
     * 报告摘要
     */
    @Data
    @Schema(description = "报告摘要")
    public static class ReportItem {

        @Schema(description = "报告ID")
        private String id;

        @Schema(description = "名称")
        private String name;

        @Schema(description = "图片数量")
        private Integer fileCount;
    }

    /**
     * 待办摘要
     */
    @Data
    @Schema(description = "待办摘要")
    public static class TodoItem {

        @Schema(description = "待办ID")
        private String id;

        @Schema(description = "标题")
        private String title;

        @Schema(description = "是否完成")
        private Boolean done;

        @Schema(description = "时刻")
        private String dueTime;
    }

    /**
     * 允许入口
     */
    @Data
    @Schema(description = "当天允许入口")
    public static class AllowedActions {

        @Schema(description = "是否可新增测量")
        private Boolean canAddMeasurement;

        @Schema(description = "是否可新增饮食")
        private Boolean canAddDiet;

        @Schema(description = "是否可新增报告")
        private Boolean canAddReport;

        @Schema(description = "是否可新增待办")
        private Boolean canAddTodo;

        @Schema(description = "是否可创建预约")
        private Boolean canBookCheckup;

        @Schema(description = "是否可完成产检")
        private Boolean canCompleteCheckup;
    }
}
