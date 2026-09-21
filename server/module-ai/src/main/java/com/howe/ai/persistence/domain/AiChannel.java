package com.howe.ai.persistence.domain;

import com.howe.common.core.domain.BaseEntity;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * AI 渠道 ai_channel
 *
 * <p>渠道 = 服务商 + 一组密钥。{@code apiKey} 在库中始终是密文，
 * 只在服务内部解密使用；接口响应一律返回掩码。</p>
 *
 * @author howe
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "AI 渠道")
public class AiChannel extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    @Schema(description = "主键ID")
    private Long id;

    @NotBlank(message = "服务商标识不能为空")
    @Schema(description = "关联 ai_provider.code", example = "dashscope")
    private String providerCode;

    @NotBlank(message = "渠道名称不能为空")
    @Size(max = 128, message = "渠道名称长度不能超过128个字符")
    @Schema(description = "渠道名称", example = "百炼-主账号")
    private String name;

    @Schema(description = "接口密钥（密文入库，接口响应返回掩码）")
    private String apiKey;

    @Schema(description = "接入点，覆盖服务商默认值")
    private String baseUrl;

    @Schema(description = "扩展配置（代理、region、组织 ID 等，JSON 对象）")
    private String extra;

    @Schema(description = "降级顺序，越小越先", example = "1")
    private Integer priority;

    @Schema(description = "是否启用（0停用 1启用）", example = "1")
    private Integer enabled;

    @Schema(description = "健康状态：UNKNOWN / UP / DOWN", example = "UNKNOWN")
    private String healthStatus;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "最近一次连通性检测时间")
    private Date lastCheckAt;
}
