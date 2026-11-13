package com.howe.common.tool.exception;

/**
 * 可映射为稳定工具错误码的业务异常。
 *
 * <p>只有本异常携带的 {@code code} 与 {@code message} 会进入工具响应；
 * 其他异常一律由平台转换为固定错误，不泄露堆栈或内部报文。</p>
 */
public final class ToolException extends RuntimeException {

    /** 稳定错误码，使用小写 snake_case。 */
    private final String code;

    /** 客户端是否可以在不改变参数的前提下重试。 */
    private final boolean retryable;

    /**
     * 构造不可重试的业务错误。
     *
     * @param code 稳定错误码
     * @param message 固定中文提示
     */
    public ToolException(String code, String message) {
        this(code, message, false);
    }

    /**
     * 构造业务错误。
     *
     * @param code 稳定错误码
     * @param message 固定中文提示
     * @param retryable 是否允许客户端重试
     */
    public ToolException(String code, String message, boolean retryable) {
        super(message);
        this.code = code;
        this.retryable = retryable;
    }

    /**
     * 返回稳定错误码。
     *
     * @return 错误码
     */
    public String code() {
        return code;
    }

    /**
     * 返回是否允许客户端重试。
     *
     * @return 可重试时返回 true
     */
    public boolean retryable() {
        return retryable;
    }
}
