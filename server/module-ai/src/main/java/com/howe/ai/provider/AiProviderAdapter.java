package com.howe.ai.provider;

import com.howe.ai.api.dto.AiChatResult;
import com.howe.ai.api.dto.AiTextChunk;
import com.howe.ai.api.dto.AiUsage;
import com.howe.ai.persistence.domain.AiChannel;
import com.howe.ai.persistence.domain.AiModel;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.ImageOptions;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * AI 协议适配器
 *
 * <p>把「渠道 + 模型」翻译成 Spring AI 的模型实例。协议标识对应
 * {@code ai_provider.protocol}，新增协议只需再实现一个适配器。</p>
 *
 * <p>能力不是每个协议都齐全，缺的能力用默认实现抛出明确错误，由调用侧转成
 * {@code AiException}。</p>
 *
 * @author howe
 */
public interface AiProviderAdapter
{
    /**
     * 协议标识
     *
     * @return 协议标识，如 {@code dashscope} / {@code openai} / {@code raw-http}
     */
    String protocol();

    /**
     * 构建对话模型
     *
     * @param channel 渠道
     * @param model   模型
     * @return 对话模型
     */
    default ChatModel createChatModel(AiChannel channel, AiModel model)
    {
        throw new UnsupportedOperationException("协议 " + protocol() + " 不支持对话能力");
    }

    /**
     * 构建文生图模型
     *
     * @param channel 渠道
     * @param model   模型
     * @return 文生图模型
     */
    default ImageModel createImageModel(AiChannel channel, AiModel model)
    {
        throw new UnsupportedOperationException("协议 " + protocol() + " 不支持文生图能力");
    }

    /**
     * 构建向量模型
     *
     * @param channel 渠道
     * @param model   模型
     * @return 向量模型
     */
    default EmbeddingModel createEmbeddingModel(AiChannel channel, AiModel model)
    {
        throw new UnsupportedOperationException("协议 " + protocol() + " 不支持向量能力");
    }

    /**
     * 构造该协议认识的对话选项
     *
     * <p>采样参数在各协议的选项类型里字段名不同，统一在这里转换。
     * 返回 null 表示该协议不支持对话，调用时按模型自身的默认参数执行。</p>
     *
     * @param model 模型
     * @param spec  采样参数
     * @return 协议对应的对话选项，可为 null
     */
    default ChatOptions chatOptions(AiModel model, ChatSpec spec)
    {
        return null;
    }

    /**
     * 构造该协议认识的文生图选项
     *
     * <p>尺寸写法、张数、负向提示词在协议间差异很大（{@code 1024*1024} 与
     * {@code 1024x1024}、有的协议不支持负向提示词），统一在这里转换，
     * 能力层不需要知道协议细节。</p>
     *
     * @param model 模型
     * @param spec  文生图参数
     * @return 协议对应的图片选项
     */
    ImageOptions imageOptions(AiModel model, ImageSpec spec);

    /**
     * 构造该协议认识的向量选项
     *
     * @param model      模型
     * @param dimensions 目标维度，可为空
     * @return 协议对应的向量选项，可为 null
     */
    default EmbeddingOptions embeddingOptions(AiModel model, Integer dimensions)
    {
        return null;
    }

    /**
     * 流式对话
     *
     * <p>默认实现走 Spring AI 的 {@code ChatModel.stream()}，只取正文增量。
     * 需要额外暴露思考内容的协议可以覆盖它、自己解析上游 SSE——Spring AI 1.1.2
     * 的 OpenAI 模块不认 {@code reasoning_content}，那个字段在它内部就被丢掉了。</p>
     *
     * <p>返回的流必须以一个 {@code done=true} 的分片收尾，分片的 {@code result}
     * 里带上用量与结束原因，能力层靠它写调用记录。</p>
     *
     * @param channel  渠道
     * @param model    模型
     * @param spec     采样参数
     * @param messages 对话消息（含 system），由调用方按顺序组装
     * @return 分片流
     */
    default Flux<AiTextChunk> streamChat(AiChannel channel, AiModel model, ChatSpec spec, List<Message> messages)
    {
        ChatOptions options = chatOptions(model, spec);
        Prompt prompt = options == null ? new Prompt(messages) : new Prompt(messages, options);
        StringBuilder buffer = new StringBuilder();
        AtomicReference<ChatResponse> lastResponse = new AtomicReference<>();
        long start = System.currentTimeMillis();
        return createChatModel(channel, model).stream(prompt)
                .doOnNext(lastResponse::set)
                // Reactor 的 map 不允许返回 null，而「没有文本的增量」是流式的常态
                // （首帧只带 role、末帧只带 finish_reason），所以必须用 handle 过滤，
                // 写成 map + filter 会在第一个空增量上抛 NPE 掐断整条流。
                .<String>handle((response, sink) ->
                {
                    String delta = extractText(response);
                    if (delta != null)
                    {
                        sink.next(delta);
                    }
                })
                .map(delta ->
                {
                    buffer.append(delta);
                    return AiTextChunk.delta(delta);
                })
                .concatWith(Flux.defer(() -> Flux.just(
                        AiTextChunk.done(summarize(model, buffer.toString(), lastResponse.get(), start)))));
    }

    /**
     * 连通性检测：发一个最小请求
     *
     * @param channel 渠道
     * @param model   模型，可为空
     * @return 检测结果
     */
    AiHealthResult healthCheck(AiChannel channel, AiModel model);

    /**
     * 查询厂商侧的可用模型列表
     *
     * <p>对应厂商的 models 接口，供后台「从渠道获取模型」使用。
     * 厂商的模型清单是动态的（新模型上线、旧模型下线），所以这里不做缓存。</p>
     *
     * <p>没有该接口的协议保持默认实现抛错，由调用侧转成「请手动添加」的提示。</p>
     *
     * @param channel 渠道
     * @return 厂商模型名集合，按名称升序
     */
    default List<String> listModels(AiChannel channel)
    {
        throw new UnsupportedOperationException("协议 " + protocol() + " 不支持获取模型列表");
    }

    /**
     * 取响应里的正文增量
     *
     * @param response 模型响应
     * @return 正文增量，没有内容时返回 null
     */
    private static String extractText(ChatResponse response)
    {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null)
        {
            return null;
        }
        String text = response.getResult().getOutput().getText();
        return text == null || text.isEmpty() ? null : text;
    }

    /**
     * 汇总结束分片里的正文、用量与结束原因
     *
     * @param model    模型
     * @param text     完整正文
     * @param response 最后一次模型响应，可为空
     * @param start    起始时间
     * @return 结果对象，provider 与 callLogId 由能力层补齐
     */
    private static AiChatResult summarize(AiModel model, String text, ChatResponse response, long start)
    {
        Usage usage = response == null || response.getMetadata() == null
                ? null : response.getMetadata().getUsage();
        AiUsage aiUsage = usage == null ? AiUsage.builder().build() : AiUsage.builder()
                .inputTokens(usage.getPromptTokens())
                .outputTokens(usage.getCompletionTokens())
                .totalTokens(usage.getTotalTokens())
                .build();
        String finishReason = response == null || response.getResult() == null
                || response.getResult().getMetadata() == null
                ? null : response.getResult().getMetadata().getFinishReason();
        return AiChatResult.builder()
                .text(text)
                .model(model.getModelName())
                .usage(aiUsage)
                .finishReason(finishReason)
                .elapsedMs(System.currentTimeMillis() - start)
                .build();
    }
}
