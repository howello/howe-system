package com.howe.ai.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 文生图结果
 *
 * @author howe
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "文生图结果")
public class AiImageResult
{
    @Schema(description = "生成图列表，url 为永久地址")
    private List<AiImage> images;

    @Schema(description = "实际使用的完整 prompt，含风格后缀")
    private String prompt;

    @Schema(description = "实际生效的服务商协议", example = "dashscope")
    private String provider;

    @Schema(description = "实际生效的模型名", example = "wanx2.1-t2i-turbo")
    private String model;

    @Schema(description = "本次调用耗时（毫秒）")
    private long elapsedMs;

    @Schema(description = "调用记录 ID")
    private Long callLogId;

    @Schema(description = "异步任务 ID。第一版同步返回，该字段恒为 null，为后续改轮询预留")
    private String taskId;
}
