package com.howe.ai.capability;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.howe.ai.api.AiErrorCode;
import com.howe.ai.api.AiException;
import com.howe.ai.api.dto.AiChatRequest;
import com.howe.ai.api.dto.AiChatResult;
import com.howe.ai.api.dto.AiMessage;
import com.howe.ai.api.dto.AiTextChunk;
import com.howe.ai.api.dto.AiUsage;
import com.howe.ai.config.AiConfigCache;
import com.howe.ai.persistence.domain.AiCallLog;
import com.howe.ai.persistence.domain.AiProvider;
import com.howe.ai.provider.AiProviderAdapter;
import com.howe.ai.provider.ChatSpec;
import com.howe.ai.route.AiRoutePlan;
import com.howe.ai.route.AiRouteResolver;
import com.howe.ai.route.AiRouteTarget;
import com.howe.ai.usage.AiCallLogRecorder;
import com.howe.ai.usage.AiRequestDigest;
import com.howe.common.utils.SecurityUtils;
import com.howe.common.utils.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 流式文本能力
 *
 * <p>只取主模型，<b>不做降级</b>：一旦给前端推过字，再切备用模型拼完整响应会得到
 * 语义错乱的文本。首个增量之后出错就直接以错误码结束流，让前端提示用户重试。</p>
 *
 * @author howe
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StreamCapability
{
    private final AiRouteResolver aiRouteResolver;

    private final AiConfigCache aiConfigCache;

    private final AiCallLogRecorder aiCallLogRecorder;

    /**
     * 流式生成文本
     *
     * @param request 对话请求
     * @return 文本分片流
     */
    public Flux<AiTextChunk> stream(AiChatRequest request)
    {
        // 计时从方法入口开始：路由解析、模型实例构建都算进这次调用的耗时。
        // 之前从 chatModel.stream() 之前才起算，导致「耗时」可能小于「首字延迟」，
        // 两个数字互相矛盾，反而没法用来定位问题。
        long start = System.currentTimeMillis();
        List<Message> messages = buildMessages(request);
        AiRoutePlan plan = aiRouteResolver.resolve(request.getScene(), AiCapability.CHAT,
                request.getChannel(), request.getModel());
        AiRouteTarget target = plan.primary();
        ChatSpec spec = mergeSpec(plan, request);
        String traceId = IdUtil.fastSimpleUUID();
        String digest = AiRequestDigest.of(request.getScene(), AiCapability.CHAT.name(), request.getChannel(),
                request.getModel(), resolvePromptText(request), countMessages(request), request.getMetadata());
        String operator = currentOperator();

        AiProvider provider = aiConfigCache.requireProvider(target.channel());
        AiProviderAdapter adapter = aiConfigCache.getAdapter(provider.getProtocol());

        StringBuilder buffer = new StringBuilder();
        AtomicBoolean delivered = new AtomicBoolean(false);
        AtomicReference<AiChatResult> upstreamResult = new AtomicReference<>();

        // 取分片交给适配器：默认实现走 Spring AI 的 ChatModel.stream()，
        // 开了「暴露思考内容」的模型由适配器自己解析上游 SSE。
        // 这条链上还会经过思考增量，正文才累进 buffer。
        return adapter.streamChat(target.channel(), target.model(), spec, messages)
                .doOnNext(chunk ->
                {
                    if (StrUtil.isNotEmpty(chunk.getDelta()))
                    {
                        buffer.append(chunk.getDelta());
                        delivered.set(true);
                    }
                    if (chunk.isDone() && chunk.getResult() != null)
                    {
                        upstreamResult.set(chunk.getResult());
                    }
                })
                // 适配器的 done 分片只用来带用量与结束原因，对外的 done 由这里重建：
                // 要补上 provider、耗时与调用记录 ID
                .filter(chunk -> !chunk.isDone())
                .concatWith(Flux.defer(() -> Flux.just(
                        finish(traceId, request, target, operator, digest, buffer.toString(),
                                upstreamResult.get(), start))))
                .onErrorResume(error ->
                {
                    // 已经推过增量：按文档约定直接以 PROVIDER_ERROR 收尾，不换模型续写
                    AiErrorCode code = delivered.get()
                            ? AiErrorCode.PROVIDER_ERROR : AiErrorClassifier.classify(error);
                    // 厂商的原因只在响应体里（「模型不存在」「密钥无效」），
                    // 所以要同时带给日志和前端，只给错误码等于把唯一的线索丢掉
                    String reason = AiErrorClassifier.rootMessage(error);
                    log.warn("流式输出中断：scene={}, model={}, delivered={}, code={}, error={}: {}",
                            request.getScene(), target.model().getModelName(), delivered.get(), code,
                            error.getClass().getSimpleName(), reason);
                    recordFailure(traceId, request, target, operator, digest, code, start);
                    return Flux.just(AiTextChunk.error(code.name(), reason));
                });
    }

    /**
     * 组装结束分片，并把本次流式调用落库
     *
     * @param traceId  链路 ID
     * @param request  请求
     * @param target   生效目标
     * @param operator 操作人
     * @param digest   入参摘要
     * @param text     完整文本
     * @param upstream 适配器给出的结束结果，只用到用量与结束原因，可为空
     * @param start    起始时间
     * @return 结束分片
     */
    private AiTextChunk finish(String traceId, AiChatRequest request, AiRouteTarget target, String operator,
            String digest, String text, AiChatResult upstream, long start)
    {
        long elapsed = System.currentTimeMillis() - start;
        AiUsage usage = upstream == null || upstream.getUsage() == null
                ? AiUsage.builder().build() : upstream.getUsage();
        AiCallLog callLog = baseLog(traceId, request, target, operator, digest, elapsed);
        callLog.setStatus("SUCCESS");
        callLog.setInputTokens(usage.getInputTokens());
        callLog.setOutputTokens(usage.getOutputTokens());
        Long callLogId = aiCallLogRecorder.record(callLog, null);

        AiChatResult result = AiChatResult.builder()
                .text(text)
                .provider(target.channel().getProviderCode())
                .model(target.model().getModelName())
                .usage(usage)
                .elapsedMs(elapsed)
                .finishReason(upstream == null ? null : upstream.getFinishReason())
                .callLogId(callLogId)
                .build();
        return AiTextChunk.done(result);
    }

    /**
     * 记录一次失败的流式调用
     *
     * @param traceId  链路 ID
     * @param request  请求
     * @param target   生效目标
     * @param operator 操作人
     * @param digest   入参摘要
     * @param code     错误码
     * @param start    起始时间
     */
    private void recordFailure(String traceId, AiChatRequest request, AiRouteTarget target, String operator,
            String digest, AiErrorCode code, long start)
    {
        AiCallLog callLog = baseLog(traceId, request, target, operator, digest,
                System.currentTimeMillis() - start);
        callLog.setStatus("FAILED");
        callLog.setErrorCode(code.name());
        aiCallLogRecorder.record(callLog, null);
    }

    /**
     * 组装调用记录通用字段
     *
     * @param traceId  链路 ID
     * @param request  请求
     * @param target   生效目标
     * @param operator 操作人
     * @param digest   入参摘要
     * @param elapsed  耗时
     * @return 调用记录
     */
    private AiCallLog baseLog(String traceId, AiChatRequest request, AiRouteTarget target, String operator,
            String digest, long elapsed)
    {
        AiCallLog callLog = new AiCallLog();
        callLog.setTraceId(traceId);
        callLog.setScene(request.getScene());
        callLog.setCapability(AiCapability.CHAT.name());
        callLog.setProviderCode(target.channel().getProviderCode());
        callLog.setChannelId(target.channel().getId());
        callLog.setModelId(target.model().getId());
        callLog.setAttempt(1);
        callLog.setElapsedMs((int) Math.min(elapsed, Integer.MAX_VALUE));
        callLog.setRequestDigest(digest);
        callLog.setOperator(operator);
        return callLog;
    }

    /**
     * 组装消息列表
     *
     * @param request 请求
     * @return 消息列表
     */
    private List<Message> buildMessages(AiChatRequest request)
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
                messages.add(toSpringMessage(message));
            }
        }
        else if (StrUtil.isNotBlank(request.getPrompt()))
        {
            messages.add(new UserMessage(request.getPrompt()));
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
     * @param message 消息
     * @return Spring AI 消息
     */
    private Message toSpringMessage(AiMessage message)
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
        return new UserMessage(content);
    }

    /**
     * 合并采样参数
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
