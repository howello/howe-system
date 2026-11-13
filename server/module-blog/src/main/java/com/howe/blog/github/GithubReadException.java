package com.howe.blog.github;

/**
 * GitHub 正文读取的受控失败原因。
 *
 * <p>仅在工具只读路径使用，避免把上游报文、路径或堆栈带到工具结果与审计。
 * 原因由调用方映射为稳定工具错误码，本类型不携带原始响应或异常文本。</p>
 */
public class GithubReadException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** 失败原因。 */
    public enum Reason {
        /** 源文件超过读取上限。 */
        TOO_LARGE,
        /** 上游不可用或返回非预期响应。 */
        UNAVAILABLE,
        /** 读取在截止时间前未完成。 */
        TIMEOUT
    }

    /** 受控原因。 */
    private final Reason reason;

    /**
     * 构造受控异常。
     *
     * @param reason 失败原因
     */
    public GithubReadException(Reason reason) {
        this(reason, null);
    }

    /**
     * 构造带内部根因的受控异常。
     *
     * <p>cause 只用于服务端日志与诊断；{@link #getMessage()} 始终使用固定文本，
     * 不会把上游异常内容回传工具调用方。</p>
     *
     * @param reason 失败原因
     * @param cause 内部根因，可为空
     */
    public GithubReadException(Reason reason, Throwable cause) {
        super("博客正文读取失败：" + reason.name(), cause, false, false);
        this.reason = reason;
    }

    /**
     * 返回失败原因。
     *
     * @return 受控原因
     */
    public Reason reason() {
        return reason;
    }
}
