package com.howe.ai.persistence.domain;

import com.howe.common.core.domain.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI 服务商 ai_provider
 *
 * <p>Provider 只描述「协议 + 默认接入点」，不持有密钥。同一个协议下可以挂多条渠道
 * （例如 OpenAI 兼容协议下同时挂 DeepSeek、月之暗面、自建 vLLM）。</p>
 *
 * @author howe
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "AI 服务商")
public class AiProvider extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    @Schema(description = "主键ID")
    private Long id;

    @NotBlank(message = "服务商标识不能为空")
    @Size(max = 64, message = "服务商标识长度不能超过64个字符")
    @Schema(description = "服务商标识，全局唯一", example = "dashscope")
    private String code;

    @NotBlank(message = "服务商名称不能为空")
    @Size(max = 128, message = "服务商名称长度不能超过128个字符")
    @Schema(description = "展示名", example = "阿里云百炼")
    private String name;

    @NotBlank(message = "适配器协议不能为空")
    @Schema(description = "适配器协议：dashscope / openai / raw-http", example = "dashscope")
    private String protocol;

    @Schema(description = "默认接入点，可被渠道覆盖", example = "https://dashscope.aliyuncs.com")
    private String defaultBaseUrl;

    @Schema(description = "支持的能力列表（JSON 数组）", example = "[\"chat\",\"vision\",\"image\",\"embedding\"]")
    private String supports;

    @Schema(description = "是否启用（0停用 1启用）", example = "1")
    private Integer enabled;

    @Schema(description = "排序，越小越靠前", example = "1")
    private Integer sort;
}
