package com.howe.pregnancy.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 每日变化时间轴
 *
 * @author howe
 */
@Data
@Schema(description = "每日变化时间轴")
public class GrowthVO {

    @Schema(description = "预产期，未配置为空")
    private String dueDate;

    @Schema(description = "业务今天")
    private String businessToday;

    @Schema(description = "按日期升序的每日内容")
    private List<DayGrowth> days;

    /**
     * 单日内容
     */
    @Data
    @Schema(description = "单日每日变化")
    public static class DayGrowth {

        @Schema(description = "日期")
        private String date;

        @Schema(description = "孕日，未配置或孕前为空")
        private Integer gestationalDay;

        @Schema(description = "孕周展示文本")
        private String gestationalAge;

        @Schema(description = "状态（AVAILABLE/DUE_DATE_UNSET/BEFORE_PREGNANCY/NO_CONTENT）")
        private String status;

        @Schema(description = "准妈妈内容")
        private SideContent mother;

        @Schema(description = "胎宝宝内容")
        private SideContent baby;
    }

    /**
     * 单侧内容
     */
    @Data
    @Schema(description = "单侧每日内容")
    public static class SideContent {

        @Schema(description = "来源（DAILY/WEEKLY）")
        private String source;

        @Schema(description = "纯文本正文")
        private String text;

        @Schema(description = "配图地址，可空")
        private String imageUrl;

        @Schema(description = "是否为本周概况")
        private Boolean weekly;
    }
}
