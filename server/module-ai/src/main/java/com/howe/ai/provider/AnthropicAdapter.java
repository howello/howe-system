package com.howe.ai.provider;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSONObject;
import com.howe.ai.api.AiErrorCode;
import com.howe.ai.api.AiException;
import com.howe.ai.api.dto.AiChatResult;
import com.howe.ai.api.dto.AiTextChunk;
import com.howe.ai.api.dto.AiUsage;
import com.howe.ai.config.AiProperties;
import com.howe.ai.config.AiSecretCipher;
import com.howe.ai.persistence.domain.AiChannel;
import com.howe.ai.persistence.domain.AiModel;
import com.howe.ai.persistence.mapper.AiProviderMapper;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeType;
import reactor.core.publisher.Flux;

import java.net.URI;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Anthropic 协议适配器
 *
 * <p>只覆盖对话与图片理解，直接调官方 Messages API。文生图与向量不在本协议范围：
 * 服务商能力声明、模型保存校验、运行时异常三层一致地拒绝，而不是静默失败。</p>
 *
 * <p><b>不发送采样参数</b>：API 版本 {@code 2026-10-21} 起 {@code temperature} /
 * {@code top_p} / {@code top_k} 一律返回 400。场景参数里若配了这三项，
 * 在 Anthropic 渠道上会被忽略——这是刻意的，否则既有的 {@code meal.*} 场景
 * （默认带 {@code temperature}）会因为切到 Anthropic 而整体不可用。</p>
 *
 * @author howe
 */
@Component
public class AnthropicAdapter extends AbstractAiProviderAdapter
{
    /** 协议标识 */
    public static final String PROTOCOL = "anthropic";

    /** 模型列表接口超时 */
    private static final int MODEL_LIST_TIMEOUT_MS = 15000;

    /** 健康检查的最小输出上限，足够验证鉴权与调用链 */
    private static final int HEALTH_MAX_TOKENS = 16;

    public AnthropicAdapter(AiSecretCipher cipher, AiProviderMapper providerMapper)
    {
        super(cipher, providerMapper);
    }

    @Override
    public String protocol()
    {
        return PROTOCOL;
    }

    /**
     * 构建同步对话模型
     *
     * <p>把 AnthropicMessagesClient 桥接成 Spring AI 的 {@link ChatModel}，
     * 这样能力层的同步调用链不需要为这个协议写分支。</p>
     *
     * @param channel 渠道
     * @param model   模型
     * @return 对话模型
     */
    @Override
    public ChatModel createChatModel(AiChannel channel, AiModel model)
    {
        AnthropicMessagesClient client = new AnthropicMessagesClient(baseUrl(channel), apiKey(channel));
        return new AnthropicChatModel(client, model, messages -> buildBody(model, messages));
    }

    /**
     * 流式对话
     *
     * <p>覆盖默认实现：Anthropic 的 SSE 事件结构与 OpenAI 不同，
     * 走 {@link ChatModel#stream} 拿不到干净的正文增量，也会丢掉用量。</p>
     *
     * @param channel  渠道
     * @param model    模型
     * @param spec     采样参数（Anthropic 只使用 maxTokens）
     * @param messages 对话消息
     * @return 分片流
     */
    @Override
    public Flux<AiTextChunk> streamChat(AiChannel channel, AiModel model, ChatSpec spec, List<Message> messages)
    {
        return Flux.create(sink ->
        {
            AnthropicMessagesClient client = new AnthropicMessagesClient(baseUrl(channel), apiKey(channel));
            JSONObject body = buildBody(model, messages);
            AtomicReference<AnthropicMessagesClient.AnthropicUsage> usage =
                    new AtomicReference<>(new AnthropicMessagesClient.AnthropicUsage(null, null));
            long start = System.currentTimeMillis();
            StringBuilder buffer = new StringBuilder();
            try
            {
                String stopReason = client.stream(body, AiProperties.load().getTimeoutChatMs(),
                        text ->
                        {
                            buffer.append(text);
                            sink.next(AiTextChunk.delta(text));
                        },
                        u -> usage.set(usage.get().merge(u)));
                AiUsage resultUsage = toUsage(usage.get());
                long elapsed = System.currentTimeMillis() - start;
                sink.next(AiTextChunk.done(AiChatResult.builder()
                        .text(buffer.toString())
                        .provider(PROTOCOL)
                        .model(model.getModelName())
                        .usage(resultUsage)
                        .elapsedMs(elapsed)
                        .finishReason(stopReason)
                        .build()));
                sink.complete();
            }
            catch (AiException e)
            {
                sink.error(e);
            }
            catch (Exception e)
            {
                sink.error(translate("Anthropic 流式调用", e));
            }
        });
    }

    /**
     * 本协议不提供文生图
     *
     * @param channel 渠道
     * @param model   模型
     * @return 永不返回
     */
    @Override
    public org.springframework.ai.image.ImageModel createImageModel(AiChannel channel, AiModel model)
    {
        throw new AiException(AiErrorCode.BAD_REQUEST, "Anthropic 协议不支持文生图能力");
    }

    /**
     * 本协议不支持采样参数
     *
     * <p>返回一个不设置 temperature / topP 的选项对象。之所以不抛异常：
     * 场景参数里带 temperature 是既有数据，抛异常会让整条路由不可用。</p>
     *
     * @param model 模型
     * @param spec  采样参数
     * @return 空选项
     */
    @Override
    public ChatOptions chatOptions(AiModel model, ChatSpec spec)
    {
        return ChatOptions.builder().build();
    }

    @Override
    public org.springframework.ai.image.ImageOptions imageOptions(AiModel model, ImageSpec spec)
    {
        throw new AiException(AiErrorCode.BAD_REQUEST, "Anthropic 协议不支持文生图能力");
    }

    /**
     * 连通性检测
     *
     * <p>优先用传入模型；没传则取模型列表接口验证鉴权。后者能证明密钥有效，
     * 但不能证明模型可调用，因此结果文案会明确区分。</p>
     *
     * @param channel 渠道
     * @param model   模型，可为空
     * @return 检测结果
     */
    @Override
    public AiHealthResult healthCheck(AiChannel channel, AiModel model)
    {
        AnthropicMessagesClient client = new AnthropicMessagesClient(baseUrl(channel), apiKey(channel));
        try
        {
            if (model == null)
            {
                List<String> models = client.listModels(MODEL_LIST_TIMEOUT_MS);
                return AiHealthResult.ok("鉴权可用，已获取 " + models.size() + " 个模型；尚未完成模型调用验证");
            }
            List<Message> messages = List.of(new UserMessage("ping"));
            JSONObject body = buildBody(model, messages);
            body.put("max_tokens", HEALTH_MAX_TOKENS);
            AnthropicMessagesClient.AnthropicMessage result =
                    client.call(body, MODEL_LIST_TIMEOUT_MS);
            return AiHealthResult.ok("连通性正常（模型 " + model.getModelName() + " 返回 "
                    + result.text().length() + " 字符）");
        }
        catch (AiException e)
        {
            return AiHealthResult.fail(e.getMessage(), e.getAiErrorCode().name());
        }
        catch (Exception e)
        {
            AiException translated = translate("Anthropic 连通性检测", e);
            return AiHealthResult.fail(translated.getMessage(), translated.getAiErrorCode().name());
        }
    }

    @Override
    public List<String> listModels(AiChannel channel)
    {
        return new AnthropicMessagesClient(baseUrl(channel), apiKey(channel))
                .listModels(MODEL_LIST_TIMEOUT_MS);
    }

    /**
     * 把 Spring AI 消息列表转成 Messages 请求体
     *
     * @param model    模型
     * @param messages 消息列表
     * @return 请求体
     */
    private JSONObject buildBody(AiModel model, List<Message> messages)
    {
        StringBuilder system = new StringBuilder();
        List<JSONObject> converted = new ArrayList<>();
        for (Message message : messages)
        {
            MessageType type = message.getMessageType();
            if (type == MessageType.SYSTEM)
            {
                if (!system.isEmpty())
                {
                    system.append('\n');
                }
                system.append(StrUtil.nullToEmpty(message.getText()));
                continue;
            }
            if (type == MessageType.ASSISTANT)
            {
                converted.add(AnthropicMessagesClient.assistantText(StrUtil.nullToEmpty(message.getText())));
                continue;
            }
            converted.add(toUserMessage(message));
        }
        // Anthropic 要求 messages 非空且首条为 user
        if (converted.isEmpty())
        {
            converted.add(AnthropicMessagesClient.userText(""));
        }
        return AnthropicMessagesClient.buildBody(model.getModelName(), system.toString(), converted,
                model.getMaxTokens());
    }

    /**
     * 把 UserMessage 转成带图片块的 user 消息
     *
     * @param message 用户消息
     * @return Messages 消息对象
     */
    private JSONObject toUserMessage(Message message)
    {
        List<Media> media = message instanceof UserMessage user ? user.getMedia() : List.of();
        if (media == null || media.isEmpty())
        {
            return AnthropicMessagesClient.userText(StrUtil.nullToEmpty(message.getText()));
        }
        List<Map<String, String>> images = new ArrayList<>();
        for (Media item : media)
        {
            images.add(toImageSource(item));
        }
        return AnthropicMessagesClient.userWithImages(StrUtil.nullToEmpty(message.getText()), images);
    }

    /**
     * 把 Spring AI 的 Media 转成 Anthropic 图片来源
     *
     * @param media 媒体
     * @return 图片来源描述
     */
    private Map<String, String> toImageSource(Media media)
    {
        Map<String, String> source = new LinkedHashMap<>();
        MimeType mimeType = media.getMimeType();
        source.put("mediaType", mimeType == null ? "image/png" : mimeType.toString());
        Object data = media.getData();
        if (data instanceof Resource resource)
        {
            try
            {
                byte[] bytes = resource.getContentAsByteArray();
                source.put("base64", Base64.getEncoder().encodeToString(bytes));
            }
            catch (Exception e)
            {
                throw new AiException(AiErrorCode.BAD_REQUEST, "读取图片失败：" + e.getMessage());
            }
        }
        else if (data instanceof URI uri)
        {
            source.put("url", uri.toString());
        }
        else if (data instanceof String text && StrUtil.startWith(text, "http"))
        {
            source.put("url", text);
        }
        else
        {
            throw new AiException(AiErrorCode.BAD_REQUEST, "不支持的图片类型：" + data);
        }
        return source;
    }

    /**
     * 转换用量对象
     *
     * @param usage Anthropic 用量
     * @return 网关用量
     */
    private AiUsage toUsage(AnthropicMessagesClient.AnthropicUsage usage)
    {
        if (usage == null)
        {
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
