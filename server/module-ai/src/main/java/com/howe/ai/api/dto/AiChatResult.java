package com.howe.ai.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文本对话结果
 *
 * @author howe
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "文本对话结果")
public class AiChatResult
{
    @Schema(description = "生成的文本")
    private String text;

    @Schema(description = "结构化输出结果，非结构化调用时为 null")
    private Object structured;

    @Schema(description = "实际生效的服务商协议", example = "dashscope")
    private String provider;

    @Schema(description = "实际生效的模型名", example = "qwen-plus")
    private String model;

    @Schema(description = "用量信息")
    private AiUsage usage;

    @Schema(description = "本次调用耗时（毫秒）")
    private long elapsedMs;

    @Schema(description = "结束原因", example = "STOP")
    private String finishReason;

    @Schema(description = "调用记录 ID，业务方可选存下来方便排查")
    private Long callLogId;
}
