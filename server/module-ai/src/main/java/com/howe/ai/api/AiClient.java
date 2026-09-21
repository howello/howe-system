package com.howe.ai.api;

import com.howe.ai.api.dto.AiChatRequest;
import com.howe.ai.api.dto.AiChatResult;
import com.howe.ai.api.dto.AiEmbedRequest;
import com.howe.ai.api.dto.AiEmbedResult;
import com.howe.ai.api.dto.AiImageRequest;
import com.howe.ai.api.dto.AiImageResult;
import com.howe.ai.api.dto.AiTextChunk;
import com.howe.ai.api.dto.AiVisionRequest;
import reactor.core.publisher.Flux;

/**
 * AI 调用门面
 *
 * <p>业务模块看到的<b>全部</b>内容就是这一组类型：注入本接口、传一个请求对象、
 * 拿一个结果对象。不接触 Spring AI，不接触任何厂商 SDK，也不需要关心模型选择、
 * 渠道切换、密钥、重试降级与图片转存。</p>
 *
 * <p>失败统一抛 {@link AiException}，按 {@link AiErrorCode} 分支即可。</p>
 *
 * @author howe
 */
public interface AiClient
{
    /**
     * 文本生成（同步）
     *
     * @param request 对话请求
     * @return 对话结果
     * @throws AiException 调用失败
     */
    AiChatResult chat(AiChatRequest request);

    /**
     * 文本生成（流式）
     *
     * <p>最后一个分片的 {@code done=true}。一旦已经推送过增量文本，
     * 后续错误直接以错误码结束流，不会切换模型续写。</p>
     *
     * @param request 对话请求
     * @return 文本分片流
     */
    Flux<AiTextChunk> chatStream(AiChatRequest request);

    /**
     * 结构化输出：按类型生成 JSON Schema 并反序列化
     *
     * @param request 对话请求
     * @param type    目标类型
     * @param <T>     目标类型
     * @return 反序列化后的对象
     * @throws AiException 调用或反序列化失败
     */
    <T> T chat(AiChatRequest request, Class<T> type);

    /**
     * 文生图：内部完成提交、轮询、下载与转存，返回永久图片地址
     *
     * @param request 文生图请求
     * @return 文生图结果
     * @throws AiException 调用或转存失败
     */
    AiImageResult image(AiImageRequest request);

    /**
     * 图片理解
     *
     * @param request 图片理解请求
     * @return 对话结果
     * @throws AiException 调用失败
     */
    AiChatResult vision(AiVisionRequest request);

    /**
     * 文本向量
     *
     * @param request 向量请求
     * @return 向量结果
     * @throws AiException 调用失败
     */
    AiEmbedResult embed(AiEmbedRequest request);
}
