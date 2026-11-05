package com.howe.ai.persistence.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * AI 调用用量统计结果
 *
 * <p>按场景、模型或天聚合后的单行结果。</p>
 *
 * @author howe
 */
@Data
@Schema(description = "AI 调用用量统计")
public class AiCallLogStats implements Serializable
{
    private static final long serialVersionUID = 1L;

    @Schema(description = "聚合键：场景标识、模型ID或日期（取决于聚合维度）")
    private String groupKey;

    /**
     * 分组展示名
     *
     * <p>{@code groupKey} 是聚合用的稳定键，不适合直接展示：按场景不能只显示
     * {@code meal.dish.cover}，按模型不能只显示一个数字 ID。展示名由 Service
     * 在聚合之后批量补齐，避免联表导致计数倍增。</p>
     */
    @Schema(description = "分组展示名：场景名称、模型名或日期")
    private String groupLabel;

    @Schema(description = "调用总次数")
    private Long totalCount;

    @Schema(description = "成功次数")
    private Long successCount;

    @Schema(description = "失败次数")
    private Long failedCount;

    @Schema(description = "输入 token 合计")
    private Long inputTokens;

    @Schema(description = "输出 token 合计")
    private Long outputTokens;

    @Schema(description = "生成图片张数合计")
    private Long imageCount;

    @Schema(description = "平均耗时（毫秒）")
    private Long avgElapsedMs;
}
