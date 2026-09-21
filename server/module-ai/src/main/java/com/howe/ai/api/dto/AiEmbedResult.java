package com.howe.ai.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 文本向量结果
 *
 * @author howe
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "文本向量结果")
public class AiEmbedResult
{
    @Schema(description = "向量列表，顺序与请求的 texts 一致")
    private List<float[]> embeddings;

    @Schema(description = "实际生效的服务商协议")
    private String provider;

    @Schema(description = "实际生效的模型名")
    private String model;

    @Schema(description = "本次调用耗时（毫秒）")
    private long elapsedMs;

    @Schema(description = "调用记录 ID")
    private Long callLogId;
}
