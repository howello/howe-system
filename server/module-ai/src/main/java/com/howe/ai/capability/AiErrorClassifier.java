package com.howe.ai.capability;

import cn.hutool.core.util.StrUtil;
import com.howe.ai.api.AiErrorCode;
import com.howe.ai.api.AiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.net.SocketTimeoutException;
import java.util.Locale;
import java.util.concurrent.TimeoutException;

/**
 * 厂商异常到 AI 错误码的翻译
 *
 * <p>协议适配器与执行模板共用同一套判定，保证「能不能降级」的结论在两层之间一致。</p>
 *
 * @author howe
 */
@Slf4j
public final class AiErrorClassifier
{
    private AiErrorClassifier()
    {
    }

    /**
     * 把任意异常翻译成 AI 异常
     *
     * @param action 动作描述，用于日志与提示
     * @param e      原始异常
     * @return AI 异常
     */
    public static AiException translate(String action, Exception e)
    {
        if (e instanceof AiException aiException)
        {
            return aiException;
        }
        log.warn("调用 {} 失败：{}", action, e.getMessage());
        return new AiException(classify(e), action + "失败：" + rootMessage(e), e);
    }

    /**
     * 判定错误码
     *
     * @param e 原始异常
     * @return 错误码
     */
    public static AiErrorCode classify(Throwable e)
    {
        if (e instanceof AiException aiException)
        {
            return aiException.getAiErrorCode();
        }
        if (e instanceof RestClientResponseException responseException)
        {
            return classifyStatus(responseException.getStatusCode(), responseException.getResponseBodyAsString());
        }
        // 流式走的是 WebClient，抛的是 WebClientResponseException 而不是 RestClient 那一支。
        // 少了这一支，401 / 400 / 429 在流式下会全部退化成 PROVIDER_ERROR，看不出真实原因。
        if (e instanceof WebClientResponseException responseException)
        {
            return classifyStatus(responseException.getStatusCode(), responseException.getResponseBodyAsString());
        }
        if (e instanceof SocketTimeoutException || e instanceof TimeoutException
                || e instanceof ResourceAccessException)
        {
            return AiErrorCode.TIMEOUT;
        }
        if (e instanceof TransientAiException)
        {
            return AiErrorCode.PROVIDER_ERROR;
        }
        if (e instanceof NonTransientAiException)
        {
            return AiErrorCode.BAD_REQUEST;
        }
        if (e instanceof UnsupportedOperationException)
        {
            return AiErrorCode.PROVIDER_ERROR;
        }
        return AiErrorCode.PROVIDER_ERROR;
    }

    /**
     * 按 HTTP 状态码判定错误码
     *
     * @param status 状态码
     * @param body   响应体
     * @return 错误码
     */
    public static AiErrorCode classifyStatus(HttpStatusCode status, String body)
    {
        String text = body == null ? "" : body.toLowerCase(Locale.ROOT);
        if (text.contains("data_inspection_failed") || text.contains("content_filter")
                || text.contains("content policy") || text.contains("sensitive")
                || text.contains("content_blocked"))
        {
            return AiErrorCode.CONTENT_BLOCKED;
        }
        if (status.isSameCodeAs(HttpStatus.UNAUTHORIZED) || status.isSameCodeAs(HttpStatus.FORBIDDEN))
        {
            return AiErrorCode.AUTH_FAILED;
        }
        if (status.isSameCodeAs(HttpStatus.TOO_MANY_REQUESTS))
        {
            return AiErrorCode.RATE_LIMITED;
        }
        if (text.contains("quota") || text.contains("insufficient") || text.contains("arrears")
                || text.contains("balance"))
        {
            return AiErrorCode.QUOTA_EXCEEDED;
        }
        if (status.is5xxServerError())
        {
            return AiErrorCode.PROVIDER_ERROR;
        }
        if (status.is4xxClientError())
        {
            return AiErrorCode.BAD_REQUEST;
        }
        return AiErrorCode.PROVIDER_ERROR;
    }

    /**
     * 取最内层异常的可读信息
     *
     * <p>厂商把真正的原因放在响应体里（「模型不存在」「余额不足」「接入点不对」），
     * 只取异常消息往往只剩一句没用的 {@code 400 Bad Request}，所以响应体优先。</p>
     *
     * @param e 原始异常
     * @return 可读信息
     */
    public static String rootMessage(Throwable e)
    {
        String body = responseBody(e);
        if (StrUtil.isNotBlank(body))
        {
            return brief(body);
        }
        Throwable current = e;
        while (current.getCause() != null && current.getCause() != current)
        {
            current = current.getCause();
        }
        return brief(StrUtil.blankToDefault(current.getMessage(), current.getClass().getSimpleName()));
    }

    /**
     * 从异常链里找 HTTP 响应体
     *
     * @param e 原始异常
     * @return 响应体，没有时返回 null
     */
    private static String responseBody(Throwable e)
    {
        Throwable current = e;
        while (current != null)
        {
            if (current instanceof RestClientResponseException responseException)
            {
                return responseException.getResponseBodyAsString();
            }
            if (current instanceof WebClientResponseException responseException)
            {
                return responseException.getResponseBodyAsString();
            }
            current = current.getCause() == current ? null : current.getCause();
        }
        return null;
    }

    /**
     * 截断过长的信息
     *
     * @param text 文本
     * @return 截断后的文本
     */
    private static String brief(String text)
    {
        return text.length() > 200 ? text.substring(0, 200) : text;
    }
}
