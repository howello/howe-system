package com.howe.ai.persistence.domain;

import com.howe.common.core.domain.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI 调用记录 ai_call_log
 *
 * <p>每次调用（含降级时的每一次尝试）落一条。{@code requestDigest} 只存脱敏后的
 * 入参摘要，绝不存完整 prompt 与密钥。</p>
 *
 * <p>{@code createTime} 复用 {@code BaseEntity} 的字段，本类不再重复声明。</p>
 *
 * @author howe
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "AI 调用记录")
public class AiCallLog extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "链路 ID，一次业务调用一个")
    private String traceId;

    @Schema(description = "场景标识")
    private String scene;

    @Schema(description = "能力类型")
    private String capability;

    @Schema(description = "实际生效的服务商标识")
    private String providerCode;

    @Schema(description = "实际生效的渠道 ID")
    private Long channelId;

    @Schema(description = "实际生效的模型 ID")
    private Long modelId;

    @Schema(description = "第几次尝试，降级会加一")
    private Integer attempt;

    @Schema(description = "输入 token 数")
    private Integer inputTokens;

    @Schema(description = "输出 token 数")
    private Integer outputTokens;

    @Schema(description = "生成图片张数")
    private Integer imageCount;

    @Schema(description = "耗时（毫秒）")
    private Integer elapsedMs;

    @Schema(description = "状态：SUCCESS / FAILED")
    private String status;

    @Schema(description = "错误码")
    private String errorCode;

    @Schema(description = "脱敏后的入参摘要")
    private String requestDigest;

    @Schema(description = "触发人")
    private String operator;
}
