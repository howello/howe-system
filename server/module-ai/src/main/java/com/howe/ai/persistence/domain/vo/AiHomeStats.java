package com.howe.ai.persistence.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 首页 AI 用量统计
 *
 * <p>基于既有 {@code ai_call_log} 聚合，今日窗口取自然日。</p>
 *
 * @param todayCalls     今日调用次数
 * @param todayTokens    今日 token 消耗合计（输入 + 输出）
 * @param successRate    今日成功率（百分比，保留一位小数）；无调用时为 0
 * @param sceneBreakdown 今日按场景调用次数，按次数倒序
 * @author howe
 */
@Schema(description = "首页 AI 用量统计")
public record AiHomeStats(
        @Schema(description = "今日调用次数") long todayCalls,
        @Schema(description = "今日 token 消耗合计") long todayTokens,
        @Schema(description = "今日成功率（百分比）") double successRate,
        @Schema(description = "今日按场景调用次数，按次数倒序") List<SceneUsage> sceneBreakdown) {

    /**
     * 场景用量条目
     *
     * @param scene 场景标识
     * @param count 调用次数
     * @author howe
     */
    @Schema(description = "场景用量条目")
    public record SceneUsage(
            @Schema(description = "场景标识") String scene,
            @Schema(description = "调用次数") long count) {
    }
}
