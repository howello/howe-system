package com.howe.ai.provider;

import com.alibaba.fastjson2.JSONObject;
import com.howe.ai.api.dto.AiUsage;
import com.howe.ai.config.AiProperties;
import com.howe.ai.persistence.domain.AiModel;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 把 Anthropic Messages API 桥接成 Spring AI 的 {@link ChatModel}
 *
 * <p>这样能力层的同步调用链不需要为 Anthropic 写分支。流式由
 * {@link AnthropicAdapter#streamChat} 直接处理，所以这里的 {@code stream}
 * 只是把单次响应包成 Flux，正常路径不会走到。</p>
 *
 * @author howe
 */
public class AnthropicChatModel implements ChatModel {

    /** 消息客户端 */
    private final AnthropicMessagesClient client;

    /** 模型配置 */
    private final AiModel model;

    /** 请求体组装器 */
    private final java.util.function.Function<List<Message>, JSONObject> bodyBuilder;

    /**
     * 构造模型
     *
     * @param client      消息客户端
     * @param model       模型配置
     * @param bodyBuilder 请求体组装器（由适配器提供，复用其消息映射逻辑）
     */
    public AnthropicChatModel(AnthropicMessagesClient client, AiModel model,
            java.util.function.Function<List<Message>, JSONObject> bodyBuilder) {
        this.client = client;
        this.model = model;
        this.bodyBuilder = bodyBuilder;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        JSONObject body = bodyBuilder.apply(prompt.getInstructions());
        AnthropicMessagesClient.AnthropicMessage message = client.call(body, AiProperties.load().getTimeoutChatMs());
        AiUsage usage = toUsage(message.usage());
        AssistantMessage output = new AssistantMessage(message.text());
        ChatGenerationMetadata metadata = ChatGenerationMetadata.builder()
                .finishReason(message.stopReason() == null ? "STOP" : message.stopReason())
                .build();
        return new ChatResponse(List.of(new Generation(output, metadata)));
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        // 流式请走 AnthropicAdapter#streamChat；这里兜底成单次响应
        return Flux.just(call(prompt));
    }

    /**
     * 转换用量对象
     *
     * @param usage Anthropic 用量
     * @return 网关用量
     */
    private AiUsage toUsage(AnthropicMessagesClient.AnthropicUsage usage) {
        if (usage == null) {
            return AiUsage.builder().build();
        }
        int input = usage.inputTokens() == null ? 0 : usage.inputTokens();
        int output = usage.outputTokens() == null ? 0 : usage.outputTokens();
        return AiUsage.builder()
                .inputTokens(usage.inputTokens())
                .outputTokens(usage.outputTokens())
                .totalTokens(input + output)
                .build();
    }
}
