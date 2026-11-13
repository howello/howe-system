package com.howe.ai.tool.dto;

/**
 * AI 只读工具的对外数据载体。
 *
 * <p>只承载白名单字段：不返回渠道 ID、端点、密钥、附加参数、调用摘要或厂商错误。
 * 用量统计不推断金额，{@code cost} 固定为 {@code null}。</p>
 */
public final class AiReadDtos {

    /** 工具数据载体不允许实例化。 */
    private AiReadDtos() {
    }

    /**
     * 模型视图。
     *
     * @param modelId 模型 ID
     * @param displayName 展示名
     * @param capability 能力类型
     * @param status 状态，{@code enabled} 或 {@code disabled}
     */
    public record ModelView(String modelId, String displayName, String capability, String status) {
    }

    /**
     * 用量统计行。
     *
     * @param groupKey 聚合键
     * @param groupLabel 展示名
     * @param totalCount 调用总数
     * @param successCount 成功数
     * @param failedCount 失败数
     * @param inputTokens 输入 token
     * @param outputTokens 输出 token
     * @param imageCount 生成图片数
     * @param avgElapsedMs 平均耗时
     * @param cost 费用；未接入厂商账单时固定为 null
     */
    public record UsageRow(String groupKey, String groupLabel, Long totalCount, Long successCount, Long failedCount,
            Long inputTokens, Long outputTokens, Long imageCount, Long avgElapsedMs, java.math.BigDecimal cost) {
    }
}
