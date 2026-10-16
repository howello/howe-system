package com.howe.ai.persistence.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 首页 AI 渠道健康
 *
 * <p>健康状态取自 {@code ai_channel.health_status}：UNKNOWN / UP / DOWN，
 * 与渠道管理页的展示口径一致。</p>
 *
 * @param channels      各渠道状态（按降级顺序），最多 8 条
 * @param downCount     异常渠道数（health_status='DOWN'）
 * @param disabledCount 停用渠道数（enabled=0）
 * @param lastCheckTime 最近一次检测时间（yyyy-MM-dd HH:mm）；从未检测时为 null
 * @author howe
 */
@Schema(description = "首页 AI 渠道健康")
public record AiChannelHealth(
        @Schema(description = "各渠道状态") List<ChannelStatus> channels,
        @Schema(description = "异常渠道数") long downCount,
        @Schema(description = "停用渠道数") long disabledCount,
        @Schema(description = "最近一次检测时间") String lastCheckTime) {

    /**
     * 渠道状态条目
     *
     * @param name   渠道名称
     * @param health 健康状态（UNKNOWN / UP / DOWN）
     * @author howe
     */
    @Schema(description = "渠道状态条目")
    public record ChannelStatus(
            @Schema(description = "渠道名称") String name,
            @Schema(description = "健康状态：UNKNOWN / UP / DOWN") String health) {
    }
}
