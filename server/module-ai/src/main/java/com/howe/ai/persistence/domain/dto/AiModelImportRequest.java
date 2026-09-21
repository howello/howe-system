package com.howe.ai.persistence.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 批量导入厂商模型请求
 *
 * <p>厂商的模型清单里没有能力类型，导入时由使用者逐项指定，所以能力类型跟着
 * 每个模型走，而不是整个请求一个值。</p>
 *
 * @author howe
 */
@Data
@Schema(description = "批量导入厂商模型请求")
public class AiModelImportRequest
{
    @NotNull(message = "归属渠道不能为空")
    @Schema(description = "归属渠道 ID")
    private Long channelId;

    @NotEmpty(message = "请至少选择一个模型")
    @Valid
    @Schema(description = "待导入的模型")
    private List<Item> models;

    /**
     * 待导入的单个模型
     */
    @Data
    @Schema(description = "待导入的模型")
    public static class Item
    {
        @NotBlank(message = "模型名不能为空")
        @Size(max = 128, message = "模型名长度不能超过128个字符")
        @Schema(description = "厂商模型名", example = "qwen-plus")
        private String modelName;

        @NotBlank(message = "能力类型不能为空")
        @Schema(description = "能力类型：CHAT / VISION / IMAGE / EMBEDDING", example = "CHAT")
        private String capability;
    }
}
