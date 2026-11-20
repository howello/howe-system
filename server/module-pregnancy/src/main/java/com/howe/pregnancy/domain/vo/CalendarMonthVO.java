package com.howe.pregnancy.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 日历月摘要
 *
 * @author howe
 */
@Data
@Schema(description = "日历月摘要")
public class CalendarMonthVO {

    @Schema(description = "业务今天")
    private String businessToday;

    @Schema(description = "预产期，未配置为空")
    private String dueDate;

    @Schema(description = "日期摘要")
    private List<DaySummary> days;

    /**
     * 单日摘要
     */
    @Data
    @Schema(description = "日历日摘要")
    public static class DaySummary {

        @Schema(description = "日期")
        private String date;

        @Schema(description = "孕周展示文本")
        private String gestationalAge;

        @Schema(description = "孕日")
        private Integer gestationalDay;

        @Schema(description = "标记（MEASUREMENT/CHECKUP/REPORT/DIET/TODO）")
        private List<String> markers;

        @Schema(description = "各类记录数量")
        private Map<String, Integer> counts;
    }
}
