package com.howe.ai.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 流式输出的文本分片
 *
 * <p>最后一个分片的 {@code done=true}，并携带完整结果与用量。
 * 出错时分片的 {@code done=true} 且 {@code errorCode} 非空——
 * 此时不会切换模型续写，前端应提示用户重试。</p>
 *
 * @author howe
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "流式输出的文本分片")
public class AiTextChunk
{
    @Schema(description = "本次增量文本")
    private String delta;

    @Schema(description = "本次增量的思考内容（推理模型的 reasoning_content），协议不返回时为空")
    private String reasoningDelta;

    @Schema(description = "是否结束")
    private boolean done;

    @Schema(description = "结束时的完整结果与用量")
    private AiChatResult result;

    @Schema(description = "出错时的错误码，此时 done 为 true")
    private String errorCode;

    @Schema(description = "出错时的可读说明，便于直接看出原因（如厂商返回的「模型不存在」）")
    private String errorMessage;

    /**
     * 构造一个增量分片
     *
     * @param delta 增量文本
     * @return 分片对象
     */
    public static AiTextChunk delta(String delta)
    {
        return AiTextChunk.builder().delta(delta).done(false).build();
    }

    /**
     * 构造一个思考内容增量分片
     *
     * @param reasoningDelta 思考内容增量
     * @return 分片对象
     */
    public static AiTextChunk reasoning(String reasoningDelta)
    {
        return AiTextChunk.builder().reasoningDelta(reasoningDelta).done(false).build();
    }

    /**
     * 构造结束分片
     *
     * @param result 完整结果
     * @return 分片对象
     */
    public static AiTextChunk done(AiChatResult result)
    {
        return AiTextChunk.builder().done(true).result(result).build();
    }

    /**
     * 构造错误结束分片
     *
     * @param errorCode    错误码
     * @param errorMessage 可读说明，可为空
     * @return 分片对象
     */
    public static AiTextChunk error(String errorCode, String errorMessage)
    {
        return AiTextChunk.builder().done(true).errorCode(errorCode).errorMessage(errorMessage).build();
    }
}
