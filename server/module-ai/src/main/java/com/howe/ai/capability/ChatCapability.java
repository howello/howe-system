package com.howe.ai.capability;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.howe.ai.api.AiErrorCode;
import com.howe.ai.api.AiException;
import com.howe.ai.api.dto.AiChatRequest;
import com.howe.ai.api.dto.AiChatResult;
import com.howe.ai.api.dto.AiMessage;
import com.howe.ai.api.dto.AiUsage;
import com.howe.ai.api.dto.AiVisionRequest;
import com.howe.ai.config.AiConfigCache;
import com.howe.ai.persistence.domain.AiCallLog;
import com.howe.ai.provider.ChatSpec;
import com.howe.ai.route.AiRoutePlan;
import com.howe.ai.route.AiRouteResolver;
import com.howe.ai.usage.AiRequestDigest;
import com.howe.common.utils.SecurityUtils;
import com.howe.common.utils.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeType;

import java.net.URI;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * 文本对话与图片理解能力
 *
 * <p>负责把请求翻译成 Spring AI 的 Prompt，执行路由、重试降级与用量落库，
 * 最后组装成业务模块看到的 {@link AiChatResult}。</p>
 *
 * @author howe
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatCapability
{
    /** 结构化输出的附加指令前缀 */
    private static final String SCHEMA_INSTRUCTION = "\n\n请严格按以下 JSON Schema 输出，不要添加额外说明文字：\n";

    /** 数据 URL 前缀 */
    private static final String DATA_URL_PREFIX = "data:";

    private final AiRouteResolver aiRouteResolver;

    private final AiConfigCache aiConfigCache;

    private final AiExecutionTemplate aiExecutionTemplate;

    /**
     * 文本生成（同步）
     *
     * @param request 对话请求
     * @return 对话结果
     */
    public AiChatResult chat(AiChatRequest request)
    {
        return invoke(request, AiCapability.CHAT, null);
    }

    /**
     * 图片理解
     *
     * @param request 图片理解请求
     * @return 对话结果
     */
    public AiChatResult vision(AiVisionRequest request)
    {
        if (request.getImages() == null || request.getImages().isEmpty())
        {
            throw new AiException(AiErrorCode.BAD_REQUEST, "图片理解至少需要提供一张图片");
        }
        return invoke(request, AiCapability.VISION, request.getImages());
    }

    /**
     * 结构化输出：按类型生成 JSON Schema 并反序列化
     *
     * @param request 对话请求
     * @param type    目标类型
     * @param <T>     目标类型
     * @return 反序列化后的对象
     */
    public <T> T structured(AiChatRequest request, Class<T> type)
    {
        if (type == null)
        {
            throw new AiException(AiErrorCode.BAD_REQUEST, "结构化输出必须指定目标类型");
        }
        BeanOutputConverter<T> converter = new BeanOutputConverter<>(type);
        AiChatResult result = invoke(request, AiCapability.CHAT, null, converter.getFormat());
        try
        {
            return converter.convert(result.getText());
        }
        catch (Exception e)
        {
            throw new AiException(AiErrorCode.PROVIDER_ERROR, "结构化输出解析失败：" + e.getMessage(), e);
        }
    }

    /**
     * 执行一次对话调用
     *
     * @param request          对话请求
     * @param capability       能力类型
     * @param images           图片列表，仅图片理解使用
     * @return 对话结果
     */
    private AiChatResult invoke(AiChatRequest request, AiCapability capability, List<String> images)
    {
        String instruction = StrUtil.isBlank(request.getOutputSchema())
                ? null : SCHEMA_INSTRUCTION + request.getOutputSchema();
        return invoke(request, capability, images, instruction);
    }

    /**
     * 执行一次对话调用
     *
     * @param request     对话请求
     * @param capability  能力类型
     * @param images      图片列表
     * @param instruction 追加到用户消息末尾的输出格式指令，可为空
     * @return 对话结果
     */
    private AiChatResult invoke(AiChatRequest request, AiCapability capability, List<String> images,
            String instruction)
    {
        List<Message> messages = buildMessages(request, images, instruction);
        AiRoutePlan plan = aiRouteResolver.resolve(request.getScene(), capability, request.getChannel(),
                request.getModel());
        ChatSpec spec = mergeSpec(plan, request);
        AiCallContext context = new AiCallContext(IdUtil.fastSimpleUUID(), request.getScene(), capability,
                AiRequestDigest.of(request.getScene(), capability.name(), request.getChannel(), request.getModel(),
                        resolvePromptText(request), countMessages(request), request.getMetadata()),
                currentOperator());

        AiInvocation<ChatResponse> invocation = aiExecutionTemplate.execute(plan, context, target ->
        {
            ChatModel chatModel = aiConfigCache.chatModel(target.channel(), target.model());
            ChatOptions options = aiConfigCache.chatOptions(target.channel(), target.model(), spec);
            Prompt prompt = options == null ? new Prompt(messages) : new Prompt(messages, options);
            return chatModel.call(prompt);
        }, (callLog, response) ->
        {
            fillUsage(callLog, response);
            return List.of();
        });

        return toResult(invocation, request);
    }

    /**
     * 组装最终结果
     *
     * @param invocation 调用结果
     * @param request    原始请求
     * @return 对话结果
     */
    private AiChatResult toResult(AiInvocation<ChatResponse> invocation, AiChatRequest request)
    {
        ChatResponse response = invocation.value();
        String text = response.getResult() == null ? "" : response.getResult().getOutput().getText();
        AiChatResult result = AiChatResult.builder()
                .text(text)
                .structured(parseStructured(text, request))
                .provider(invocation.target().channel().getProviderCode())
                .model(invocation.target().model().getModelName())
                .usage(readUsage(response))
                .elapsedMs(invocation.elapsedMs())
                .finishReason(readFinishReason(response))
                .callLogId(invocation.callLogId())
                .build();
        return result;
    }

    /**
     * 按请求的 outputSchema 解析结构化结果
     *
     * @param text    模型输出
     * @param request 原始请求
     * @return 解析后的对象；未要求结构化输出时返回 null
     */
    private Object parseStructured(String text, AiChatRequest request)
    {
        if (StrUtil.isBlank(request.getOutputSchema()) || StrUtil.isBlank(text))
        {
            return null;
        }
        try
        {
            return JSON.parse(text);
        }
        catch (Exception e)
        {
            throw new AiException(AiErrorCode.PROVIDER_ERROR, "结构化输出解析失败：" + e.getMessage(), e);
        }
    }

    /**
     * 组装消息列表
     *
     * @param request     对话请求
     * @param images      图片列表
     * @param instruction 追加指令
     * @return 消息列表
     */
    private List<Message> buildMessages(AiChatRequest request, List<String> images, String instruction)
    {
        List<Message> messages = new ArrayList<>();
        if (StrUtil.isNotBlank(request.getSystem()))
        {
            messages.add(new SystemMessage(request.getSystem()));
        }
        if (request.getMessages() != null && !request.getMessages().isEmpty())
        {
            for (AiMessage message : request.getMessages())
            {
                messages.add(toSpringMessage(message, images, instruction));
            }
        }
        else if (StrUtil.isNotBlank(request.getPrompt()))
        {
            messages.add(buildUserMessage(request.getPrompt() + StrUtil.nullToEmpty(instruction), images));
        }
        else
        {
            throw new AiException(AiErrorCode.BAD_REQUEST, "请求必须提供 messages 或 prompt");
        }
        return messages;
    }

    /**
     * 转换单条消息
     *
     * @param message     消息
     * @param images      图片列表
     * @param instruction 追加指令
     * @return Spring AI 消息
     */
    private Message toSpringMessage(AiMessage message, List<String> images, String instruction)
    {
        String content = StrUtil.nullToEmpty(message.getContent());
        String role = StrUtil.nullToEmpty(message.getRole());
        if (AiMessage.ROLE_SYSTEM.equalsIgnoreCase(role))
        {
            return new SystemMessage(content);
        }
        if (AiMessage.ROLE_ASSISTANT.equalsIgnoreCase(role))
        {
            return new AssistantMessage(content);
        }
        return buildUserMessage(content + StrUtil.nullToEmpty(instruction), images);
    }

    /**
     * 构造用户消息，图片理解时把图片作为多模态内容附上
     *
     * @param text   文本
     * @param images 图片列表
     * @return 用户消息
     */
    private UserMessage buildUserMessage(String text, List<String> images)
    {
        if (images == null || images.isEmpty())
        {
            return new UserMessage(text);
        }
        List<Media> media = new ArrayList<>(images.size());
        for (String image : images)
        {
            media.add(toMedia(image));
        }
        return UserMessage.builder().text(text).media(media).build();
    }

    /**
     * 把图片地址或内联数据转换成多模态媒体
     *
     * @param image URL 或 data:image/...;base64, 形式
     * @return 媒体对象
     */
    private Media toMedia(String image)
    {
        if (StrUtil.isBlank(image))
        {
            throw new AiException(AiErrorCode.BAD_REQUEST, "图片地址不能为空");
        }
        if (image.startsWith(DATA_URL_PREFIX))
        {
            int comma = image.indexOf(',');
            int semicolon = image.indexOf(';');
            if (comma < 0 || semicolon < 0 || semicolon > comma)
            {
                throw new AiException(AiErrorCode.BAD_REQUEST, "内联图片格式不合法");
            }
            MimeType mimeType = MimeType.valueOf(image.substring(DATA_URL_PREFIX.length(), semicolon));
            byte[] data;
            try
            {
                data = Base64.getDecoder().decode(image.substring(comma + 1));
            }
            catch (IllegalArgumentException e)
            {
                throw new AiException(AiErrorCode.BAD_REQUEST, "内联图片不是合法的 base64", e);
            }
            return Media.builder().mimeType(mimeType).data(new ByteArrayResource(data)).build();
        }
        try
        {
            return Media.builder().mimeType(MimeType.valueOf("image/*")).data(URI.create(image)).build();
        }
        catch (IllegalArgumentException e)
        {
            throw new AiException(AiErrorCode.BAD_REQUEST, "图片地址不合法：" + image, e);
        }
    }

    /**
     * 合并采样参数：全局默认 &lt; 场景 params &lt; 请求显式字段
     *
     * @param plan    路由计划
     * @param request 请求
     * @return 采样参数
     */
    private ChatSpec mergeSpec(AiRoutePlan plan, AiChatRequest request)
    {
        Double temperature = request.getTemperature();
        Integer maxTokens = request.getMaxTokens();
        Double topP = request.getTopP();
        if (temperature == null && plan.params().containsKey("temperature"))
        {
            temperature = plan.params().getDouble("temperature");
        }
        if (maxTokens == null && plan.params().containsKey("maxTokens"))
        {
            maxTokens = plan.params().getInteger("maxTokens");
        }
        if (topP == null && plan.params().containsKey("topP"))
        {
            topP = plan.params().getDouble("topP");
        }
        return new ChatSpec(temperature, maxTokens, topP);
    }

    /**
     * 取用于摘要的提示词文本
     *
     * @param request 请求
     * @return 提示词
     */
    private String resolvePromptText(AiChatRequest request)
    {
        if (StrUtil.isNotBlank(request.getPrompt()))
        {
            return request.getPrompt();
        }
        if (request.getMessages() == null || request.getMessages().isEmpty())
        {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        for (AiMessage message : request.getMessages())
        {
            builder.append(StrUtil.nullToEmpty(message.getContent()));
        }
        return builder.toString();
    }

    /**
     * 统计消息条数
     *
     * @param request 请求
     * @return 消息条数
     */
    private int countMessages(AiChatRequest request)
    {
        return request.getMessages() == null ? 0 : request.getMessages().size();
    }

    /**
     * 把用量写进调用记录
     *
     * @param callLog  调用记录
     * @param response 模型响应
     */
    private void fillUsage(AiCallLog callLog, ChatResponse response)
    {
        AiUsage usage = readUsage(response);
        callLog.setInputTokens(usage.getInputTokens());
        callLog.setOutputTokens(usage.getOutputTokens());
    }

    /**
     * 读取用量
     *
     * @param response 模型响应
     * @return 用量
     */
    private AiUsage readUsage(ChatResponse response)
    {
        Usage usage = response.getMetadata() == null ? null : response.getMetadata().getUsage();
        if (usage == null)
        {
            return AiUsage.builder().build();
        }
        return AiUsage.builder()
                .inputTokens(usage.getPromptTokens())
                .outputTokens(usage.getCompletionTokens())
                .totalTokens(usage.getTotalTokens())
                .build();
    }

    /**
     * 读取结束原因
     *
     * @param response 模型响应
     * @return 结束原因
     */
    private String readFinishReason(ChatResponse response)
    {
        if (response.getResult() == null || response.getResult().getMetadata() == null)
        {
            return null;
        }
        return response.getResult().getMetadata().getFinishReason();
    }

    /**
     * 取当前操作人，未登录时返回 system
     *
     * @return 操作人
     */
    private String currentOperator()
    {
        try
        {
            String username = SecurityUtils.getUsername();
            return StringUtils.isEmpty(username) ? "system" : username;
        }
        catch (Exception e)
        {
            return "system";
        }
    }
}
