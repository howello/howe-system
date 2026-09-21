package com.howe.ai.capability;

import cn.hutool.core.thread.ThreadUtil;
import com.howe.ai.api.AiErrorCode;
import com.howe.ai.api.AiException;
import com.howe.ai.config.AiConfigCache;
import com.howe.ai.config.AiProperties;
import com.howe.ai.persistence.domain.AiCallLog;
import com.howe.ai.persistence.domain.AiImageAsset;
import com.howe.ai.route.AiFallbackPolicy;
import com.howe.ai.route.AiRoutePlan;
import com.howe.ai.route.AiRouteTarget;
import com.howe.ai.usage.AiCallLogRecorder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * 调用执行模板
 *
 * <p>把「重试与降级」这件事收敛到一处：</p>
 * <ul>
 *   <li>同模型内的瞬时错误按 {@code ai.retry.max} 与 {@code ai.retry.backoffMs} 重试；</li>
 *   <li>可降级的错误才换下一个模型，且降级链只在场景路由命中时才有；</li>
 *   <li>重试与降级分别计数，{@code ai_call_log.attempt} 记录真实的第几次尝试；</li>
 *   <li>每次尝试都单独落一条调用记录。</li>
 * </ul>
 *
 * @author howe
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiExecutionTemplate
{
    private final AiConfigCache aiConfigCache;

    private final AiCallLogRecorder aiCallLogRecorder;

    /**
     * 按路由计划执行调用
     *
     * @param plan      路由计划
     * @param context   调用上下文
     * @param call      单次调用
     * @param enricher  成功时的记录补充，可为空
     * @param <T>       结果类型
     * @return 成功的调用结果
     * @throws AiException 全部候选都失败，或遇到不可降级的错误
     */
    public <T> AiInvocation<T> execute(AiRoutePlan plan, AiCallContext context, AiCall<T> call,
            AiSuccessEnricher<T> enricher)
    {
        AiProperties properties = aiConfigCache.getProperties();
        int maxRetries = Math.max(0, properties.getRetryMax());
        long backoffMs = Math.max(0L, properties.getRetryBackoffMs());
        List<AiRouteTarget> targets = plan.targets();

        int attempt = 0;
        AiException lastError = null;
        AiRouteTarget lastTarget = null;

        for (int index = 0; index < targets.size(); index++)
        {
            AiRouteTarget target = targets.get(index);
            lastTarget = target;
            for (int retry = 0; retry <= maxRetries; retry++)
            {
                attempt++;
                long start = System.currentTimeMillis();
                try
                {
                    T value = call.call(target);
                    long elapsed = System.currentTimeMillis() - start;
                    Long callLogId = recordSuccess(context, target, attempt, elapsed, value, enricher);
                    return new AiInvocation<>(value, target, elapsed, callLogId);
                }
                catch (Exception e)
                {
                    long elapsed = System.currentTimeMillis() - start;
                    AiException translated = AiErrorClassifier.translate(
                            context.capability().name() + " 调用", e);
                    AiErrorCode code = translated.getAiErrorCode();
                    recordFailure(context, target, attempt, elapsed, code, translated.getMessage());

                    boolean exhausted = retry >= maxRetries || !AiFallbackPolicy.canRetry(code);
                    if (!exhausted)
                    {
                        ThreadUtil.sleep(backoffMs * (retry + 1));
                        continue;
                    }
                    lastError = translated;
                    break;
                }
            }

            if (lastError != null && !AiFallbackPolicy.canFallback(lastError.getAiErrorCode()))
            {
                throw lastError;
            }
            if (index < targets.size() - 1)
            {
                log.warn("模型 {} 调用失败（{}），尝试降级到下一个候选",
                        lastTarget.model().getModelName(), lastError == null ? "-" : lastError.getAiErrorCode());
            }
        }

        if (lastError != null && targets.size() == 1)
        {
            throw lastError;
        }
        throw new AiException(AiErrorCode.ALL_FALLBACK_FAILED,
                "所有可用模型均调用失败" + (lastError == null ? "" : "：" + lastError.getMessage()));
    }

    /**
     * 记录一次成功的调用
     *
     * @param context   调用上下文
     * @param target    生效目标
     * @param attempt   第几次尝试
     * @param elapsedMs 耗时
     * @param value     结果
     * @param enricher  记录补充
     * @param <T>       结果类型
     * @return 调用记录 ID
     */
    private <T> Long recordSuccess(AiCallContext context, AiRouteTarget target, int attempt, long elapsedMs,
            T value, AiSuccessEnricher<T> enricher)
    {
        AiCallLog callLog = baseLog(context, target, attempt, elapsedMs);
        callLog.setStatus("SUCCESS");
        List<AiImageAsset> assets = Collections.emptyList();
        if (enricher != null)
        {
            List<AiImageAsset> enriched = enricher.enrich(callLog, value);
            if (enriched != null)
            {
                assets = enriched;
            }
        }
        return aiCallLogRecorder.record(callLog, assets);
    }

    /**
     * 记录一次失败的调用
     *
     * @param context   调用上下文
     * @param target    生效目标
     * @param attempt   第几次尝试
     * @param elapsedMs 耗时
     * @param code      错误码
     * @param message   错误信息
     */
    private void recordFailure(AiCallContext context, AiRouteTarget target, int attempt, long elapsedMs,
            AiErrorCode code, String message)
    {
        log.warn("AI 调用失败：scene={}, model={}, attempt={}, code={}, message={}",
                context.scene(), target.model().getModelName(), attempt, code, message);
        AiCallLog callLog = baseLog(context, target, attempt, elapsedMs);
        callLog.setStatus("FAILED");
        callLog.setErrorCode(code.name());
        aiCallLogRecorder.record(callLog, null);
    }

    /**
     * 组装调用记录的通用字段
     *
     * @param context   调用上下文
     * @param target    生效目标
     * @param attempt   第几次尝试
     * @param elapsedMs 耗时
     * @return 调用记录
     */
    private AiCallLog baseLog(AiCallContext context, AiRouteTarget target, int attempt, long elapsedMs)
    {
        AiCallLog callLog = new AiCallLog();
        callLog.setTraceId(context.traceId());
        callLog.setScene(context.scene());
        callLog.setCapability(context.capability().name());
        callLog.setProviderCode(target.channel().getProviderCode());
        callLog.setChannelId(target.channel().getId());
        callLog.setModelId(target.model().getId());
        callLog.setAttempt(attempt);
        callLog.setElapsedMs((int) Math.min(elapsedMs, Integer.MAX_VALUE));
        callLog.setRequestDigest(context.digest());
        callLog.setOperator(context.operator());
        return callLog;
    }
}
