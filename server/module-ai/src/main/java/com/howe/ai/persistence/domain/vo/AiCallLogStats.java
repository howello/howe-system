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

    @Schema(description = "聚合键：场景标识、模型名或日期（取决于聚合维度）")
    private String groupKey;

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
