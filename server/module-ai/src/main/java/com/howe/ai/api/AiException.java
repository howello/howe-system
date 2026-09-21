package com.howe.ai.api;

import com.howe.common.exception.ServiceException;

/**
 * AI 调用异常
 *
 * <p>继承 {@link ServiceException}，因此会被全局异常处理器按业务异常处理，
 * 把可读的 message 与错误码返回给前端；业务模块只需按 {@link AiErrorCode} 分支，
 * 不需要解析任何厂商原始异常。</p>
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
        super(message, aiErrorCode.ordinal());
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
        super(message, aiErrorCode.ordinal());
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
