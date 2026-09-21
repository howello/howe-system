package com.howe.ai.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用量信息
 *
 * @author howe
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "用量信息")
public class AiUsage
{
    @Schema(description = "输入 token 数")
    private Integer inputTokens;

    @Schema(description = "输出 token 数")
    private Integer outputTokens;

    @Schema(description = "总 token 数")
    private Integer totalTokens;
}
