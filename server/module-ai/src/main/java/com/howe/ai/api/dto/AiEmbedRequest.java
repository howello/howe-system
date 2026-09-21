package com.howe.ai.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 文本向量请求
 *
 * <p>只负责把文本算成向量，存与查由业务方负责。</p>
 *
 * @author howe
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "文本向量请求")
public class AiEmbedRequest
{
    @Schema(description = "场景标识，路由的 key", example = "blog.article.embed")
    private String scene;

    @Schema(description = "待向量化的文本列表")
    private List<String> texts;

    @Schema(description = "目标维度，留空用模型默认")
    private Integer dimensions;

    @Schema(description = "指定模型名，留空走场景路由或全局默认")
    private String model;

    @Schema(description = "指定渠道 ID，留空走场景路由或全局默认")
    private String channel;

    @Schema(description = "超时（毫秒），留空取全局配置")
    private Long timeoutMs;
}
