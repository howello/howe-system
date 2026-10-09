package com.howe.ai.api;

import com.howe.common.exception.ServiceException;

/**
 * AI 调用异常
 *
 * <p>继承 {@link ServiceException}，因此会被全局异常处理器按业务异常处理，把可读的
 * message 以标准业务错误码（500）返回给前端。AI 错误码只用于服务端分支：调用方按
 * {@link #getAiErrorCode()} 取 {@link AiErrorCode}，不需要解析任何厂商原始异常。</p>
 *
 * <p>注意：不要把 {@link AiErrorCode} 的枚举序号塞进 {@code ServiceException.code}。
 * 那个 code 是响应体里的业务状态码，前端按 200/500 等标准值判定成败；枚举序号从 0
 * 开始，{@code AI_DISABLED} 恰好是 0，会被前端当成成功而吞掉错误。</p>
 *
 * @author howe
 */
public class AiException extends ServiceException
{
    private static final long serialVersionUID = 1L;

    /** AI 业务错误码 */
    private final AiErrorCode aiErrorCode;

    /**
     * 用错误码的默认提示语构造异常
     *
     * @param aiErrorCode AI 错误码
     */
    public AiException(AiErrorCode aiErrorCode)
    {
        this(aiErrorCode, aiErrorCode.getMessage());
    }

    /**
     * 用自定义提示语构造异常
     *
     * @param aiErrorCode AI 错误码
     * @param message     面向用户可读的提示语
     */
    public AiException(AiErrorCode aiErrorCode, String message)
    {
        super(message);
        this.aiErrorCode = aiErrorCode;
    }

    /**
     * 用自定义提示语与底层原因构造异常
     *
     * @param aiErrorCode AI 错误码
     * @param message     面向用户可读的提示语
     * @param cause       底层原因，只用于日志排查，不外发
     */
    public AiException(AiErrorCode aiErrorCode, String message, Throwable cause)
    {
        super(message);
        this.aiErrorCode = aiErrorCode;
        initCause(cause);
    }

    /**
     * 获取 AI 业务错误码
     *
     * @return AI 错误码
     */
    public AiErrorCode getAiErrorCode()
    {
        return aiErrorCode;
    }
}
