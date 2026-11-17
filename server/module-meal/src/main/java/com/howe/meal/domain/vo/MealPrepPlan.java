package com.howe.meal.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 一键备菜清单
 *
 * <p>由 AI 按下单明细生成，只存 Redis（TTL 10 天），不落数据库。
 * 清单按采购区域分组，每个分组内主菜在前、配菜在后；每条食材是「合并后的规范名称」，
 * 明细行保留原始品种或来源菜品，便于核对。</p>
 *
 * @author howe
 */
@Data
@Schema(description = "一键备菜清单")
public class MealPrepPlan implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 生成状态：生成中。 */
    public static final String STATUS_GENERATING = "GENERATING";

    /** 生成状态：已生成。 */
    public static final String STATUS_READY = "READY";

    /** 生成状态：生成失败。 */
    public static final String STATUS_FAILED = "FAILED";

    /**
     * 生成状态：订单已结束
     *
     * <p>完成、取消、无人接单都归入此状态。清单已清理且不会再生成，
     * 前端据此隐藏「重新生成」。</p>
     */
    public static final String STATUS_CLOSED = "CLOSED";

    @Schema(description = "订单ID", example = "1")
    private Long orderId;

    @Schema(description = "生成状态：GENERATING/READY/FAILED/CLOSED", example = "READY")
    private String status;

    @Schema(description = "生成时间（毫秒时间戳）")
    private Long generatedAt;

    @Schema(description = "失败原因，仅 FAILED 时非空")
    private String message;

    @Schema(description = "按采购区域分组后的清单")
    private List<Group> groups = new ArrayList<>();

    /**
     * 采购区域分组
     */
    @Data
    @Schema(description = "采购区域分组")
    public static class Group implements Serializable {
        private static final long serialVersionUID = 1L;

        @Schema(description = "采购区域名称，如肉摊、菜摊、水产摊", example = "肉摊")
        private String market;

        @Schema(description = "分组内条目，主菜在前、配菜在后")
        private List<Item> items = new ArrayList<>();
    }

    /**
     * 合并后的食材条目
     */
    @Data
    @Schema(description = "合并后的食材条目")
    public static class Item implements Serializable {
        private static final long serialVersionUID = 1L;

        @Schema(description = "合并后的规范名称", example = "辣椒")
        private String name;

        @Schema(description = "生活化购买数量", example = "5 个")
        private String amount;

        @Schema(description = "是否主料（主菜用料），true 排在分组前面", example = "true")
        private boolean main;

        @Schema(description = "明细行：原始品种或来源菜品")
        private List<Detail> details = new ArrayList<>();
    }

    /**
     * 食材明细行
     */
    @Data
    @Schema(description = "食材明细行")
    public static class Detail implements Serializable {
        private static final long serialVersionUID = 1L;

        @Schema(description = "原始品种或菜名", example = "红椒")
        private String label;

        @Schema(description = "该品种对应的购买数量", example = "3 个")
        private String amount;
    }
}
