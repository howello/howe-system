package com.howe.ai.persistence.domain;

import com.howe.common.core.domain.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI 模型 ai_model
 *
 * <p>{@code modelName} 是传给厂商的真实名字（如 {@code qwen-plus}），
 * 与展示名分开，避免厂商改名时影响业务。</p>
 *
 * @author howe
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "AI 模型")
public class AiModel extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    @Schema(description = "主键ID")
    private Long id;

    @NotNull(message = "归属渠道不能为空")
    @Schema(description = "归属渠道 ID")
    private Long channelId;

    @NotBlank(message = "能力类型不能为空")
    @Schema(description = "能力类型：CHAT / VISION / IMAGE / EMBEDDING", example = "CHAT")
    private String capability;

    @NotBlank(message = "模型名不能为空")
    @Size(max = 128, message = "模型名长度不能超过128个字符")
    @Schema(description = "传给厂商的模型名", example = "qwen-plus")
    private String modelName;

    @Size(max = 128, message = "展示名长度不能超过128个字符")
    @Schema(description = "展示名", example = "通义千问 Plus")
    private String displayName;

    @Schema(description = "最大输出 token 数")
    private Integer maxTokens;

    @Schema(description = "扩展配置（JSON 对象）。extraBody 原样并入厂商请求体，"
            + "reasoning 为 true 时流式自己解析上游、把思考内容也推给前端")
    private String extra;

    @Schema(description = "是否启用（0停用 1启用）", example = "1")
    private Integer enabled;

    /** 渠道信息，列表查询时联表带出，不落库 */
    @Schema(description = "归属渠道名称，仅查询时带出")
    private String channelName;

    /** 渠道绑定的服务商标识，列表查询时联表带出，不落库 */
    @Schema(description = "归属渠道的服务商标识，仅查询时带出")
    private String providerCode;
}
