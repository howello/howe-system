package com.howe.ai.persistence.domain.vo;

import com.howe.ai.persistence.domain.AiCallLog;
import com.howe.ai.persistence.domain.AiImageAsset;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * AI 调用记录详情
 *
 * <p>在原始记录之上补齐「可读的名称」与生成图：调用记录本身只存
 * {@code channel_id} / {@code model_id} / {@code scene}，直接展示裸 ID
 * 对排查没有帮助。</p>
 *
 * <p>名称解析<b>不受「仅启用配置」的缓存限制</b>：渠道或模型被停用、删除后，
 * 历史记录仍要显示当时的名称，或明确的「已删除」占位。</p>
 *
 * @author howe
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "AI 调用记录详情")
public class AiCallLogDetailVO extends AiCallLog
{
    private static final long serialVersionUID = 1L;

    @Schema(description = "场景展示名称；场景已删除时回退为场景标识")
    private String sceneName;

    @Schema(description = "服务商展示名")
    private String providerName;

    @Schema(description = "渠道名称；渠道已删除时为「已删除渠道 #ID」")
    private String channelName;

    @Schema(description = "模型名称；模型已删除时为「已删除模型 #ID」")
    private String modelName;

    /**
     * 该次调用生成的图片
     *
     * <p>只有文生图场景会有值；其余能力返回空数组而不是 null，
     * 前端可以统一按「无生成图片」处理。</p>
     */
    @Schema(description = "生成图资产列表，无图时为空数组")
    private List<AiImageAsset> images;
}
