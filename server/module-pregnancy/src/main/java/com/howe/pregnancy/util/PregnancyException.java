package com.howe.pregnancy.util;

import com.howe.pregnancy.constant.PregnancyConstants;

import java.util.Map;

/**
 * 孕期业务异常
 *
 * <p>携带稳定的 {@code errorCode} 与 HTTP 状态，供前端按错误语义分支处理
 * （刷新版本、提示只读、回登录等），而不是解析中文提示文本。</p>
 *
 * <p><b>刻意不继承 {@code ServiceException}：</b>全局 {@code GlobalExceptionHandler}
 * 对 {@code ServiceException} 的分支会把结果包成 HTTP 200 + body.code 的旧形状，
 * 而本模块契约要求 HTTP 状态与 {@code code} 同义。独立异常类型可确保本模块的
 * 处理器接管，且不影响既有接口行为。</p>
 *
 * @author howe
 */
public class PregnancyException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** 稳定错误码 */
    private final String errorCode;

    /** 请求 ID，便于前后端对账 */
    private final String requestId;

    /** 对应的 HTTP 状态码 */
    private final int httpStatus;

    /** 附加数据，随错误体一起返回；可为空 */
    private final Map<String, Object> details;

    /**
     * 构造孕期业务异常
     *
     * @param errorCode 稳定错误码
     * @param message   可直接展示的中文提示
     * @param httpCode  对应的 HTTP 状态码
     */
    public PregnancyException(String errorCode, String message, Integer httpCode) {
        this(errorCode, message, httpCode, null, null);
    }

    /**
     * 构造带请求 ID 的孕期业务异常
     *
     * @param errorCode 稳定错误码
     * @param message   可直接展示的中文提示
     * @param httpCode  对应的 HTTP 状态码
     * @param requestId 请求 ID
     */
    public PregnancyException(String errorCode, String message, Integer httpCode, String requestId) {
        this(errorCode, message, httpCode, requestId, null);
    }

    /**
     * 构造带附加数据的孕期业务异常
     *
     * @param errorCode 稳定错误码
     * @param message   可直接展示的中文提示
     * @param httpCode  对应的 HTTP 状态码
     * @param requestId 请求 ID
     * @param details   附加数据，可为空
     */
    public PregnancyException(String errorCode, String message, Integer httpCode, String requestId,
                              Map<String, Object> details) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpCode == null ? 500 : httpCode;
        this.requestId = requestId;
        this.details = details;
    }

    /**
     * 取附加数据
     *
     * @return 附加数据，可能为空
     */
    public Map<String, Object> getDetails() {
        return details;
    }

    /**
     * 取对应的 HTTP 状态码
     *
     * @return HTTP 状态码
     */
    public int getHttpStatus() {
        return httpStatus;
    }

    /**
     * 取稳定错误码
     *
     * @return 错误码
     */
    public String getErrorCode() {
        return errorCode;
    }

    /**
     * 取请求 ID
     *
     * @return 请求 ID，可能为空
     */
    public String getRequestId() {
        return requestId;
    }

    /**
     * 资源不存在或当前账号无读取资格
     *
     * <p>两种情形统一返回该结果，避免通过 404/403 差异探测他人资源是否存在。</p>
     *
     * @return 异常实例
     */
    public static PregnancyException notFound() {
        return new PregnancyException(PregnancyConstants.ERROR_RESOURCE_NOT_FOUND,
                "资源不存在或无权访问", 404);
    }

    /**
     * 角色不足
     *
     * @param action 被拒绝的操作，用于拼接可读提示
     * @return 异常实例
     */
    public static PregnancyException forbidden(String action) {
        return new PregnancyException(PregnancyConstants.ERROR_FORBIDDEN,
                "当前角色无权" + action, 403);
    }

    /**
     * 未认证
     *
     * @return 异常实例
     */
    public static PregnancyException unauthenticated() {
        return new PregnancyException(PregnancyConstants.ERROR_UNAUTHENTICATED,
                "登录状态已过期，请重新登录", 401);
    }

    /**
     * 版本冲突
     *
     * @return 异常实例
     */
    public static PregnancyException versionConflict() {
        return new PregnancyException(PregnancyConstants.ERROR_VERSION_CONFLICT,
                "记录已被修改，请刷新后重试", 409);
    }

    /**
     * 版本冲突，带自定义提示
     *
     * @param message 可直接展示的提示
     * @return 异常实例
     */
    public static PregnancyException versionConflict(String message) {
        return new PregnancyException(PregnancyConstants.ERROR_VERSION_CONFLICT, message, 409);
    }

    /**
     * 幂等冲突
     *
     * @param requestId 冲突的请求 ID
     * @return 异常实例
     */
    public static PregnancyException idempotencyConflict(String requestId) {
        return new PregnancyException(PregnancyConstants.ERROR_IDEMPOTENCY_CONFLICT,
                "同一请求标识不能用于不同内容", 409, requestId);
    }

    /**
     * 幂等行已被软删
     *
     * @param requestId 请求 ID
     * @return 异常实例
     */
    public static PregnancyException requestConsumed(String requestId) {
        return new PregnancyException(PregnancyConstants.ERROR_IDEMPOTENCY_CONFLICT,
                "该请求对应的资源已被删除，不能重放", 409, requestId);
    }

    /**
     * 校验失败
     *
     * @param message 可直接展示的中文提示
     * @return 异常实例
     */
    public static PregnancyException invalid(String message) {
        return new PregnancyException(PregnancyConstants.ERROR_VALIDATION, message, 400);
    }

    /**
     * 实际记录不得晚于业务今天
     *
     * @param field 涉及的字段或记录类型
     * @return 异常实例
     */
    public static PregnancyException futureRecord(String field) {
        return new PregnancyException(PregnancyConstants.ERROR_FUTURE_RECORD,
                field + "不能填写未来日期", 400);
    }

    /**
     * 非法状态转换
     *
     * @param message 可直接展示的提示
     * @return 异常实例
     */
    public static PregnancyException invalidState(String message) {
        return new PregnancyException(PregnancyConstants.ERROR_INVALID_STATE_TRANSITION, message, 409);
    }

    /**
     * 资源被占用
     *
     * @param message 可直接展示的提示
     * @return 异常实例
     */
    public static PregnancyException resourceInUse(String message) {
        return new PregnancyException(PregnancyConstants.ERROR_RESOURCE_IN_USE, message, 409);
    }

    /**
     * 文件过大
     *
     * @param maxBytes 允许的最大字节数
     * @return 异常实例
     */
    public static PregnancyException fileTooLarge(long maxBytes) {
        return new PregnancyException(PregnancyConstants.ERROR_FILE_TOO_LARGE,
                "文件超过上限（" + (maxBytes / 1024 / 1024) + " MiB），请选择更小的原文件", 413);
    }

    /**
     * 不支持的文件类型
     *
     * @param mimeType 实际 MIME 类型
     * @return 异常实例
     */
    public static PregnancyException unsupportedFileType(String mimeType) {
        return new PregnancyException(PregnancyConstants.ERROR_UNSUPPORTED_FILE_TYPE,
                "仅支持 JPEG、PNG、WebP 原文件" + (mimeType == null ? "" : "（收到 " + mimeType + "）"), 415);
    }

    /**
     * 导出已过期
     *
     * @return 异常实例
     */
    public static PregnancyException exportExpired() {
        return new PregnancyException(PregnancyConstants.ERROR_EXPORT_EXPIRED,
                "导出结果已过期，请重新申请", 410);
    }

    /**
     * 请求过于频繁
     *
     * @return 异常实例
     */
    public static PregnancyException rateLimited() {
        return new PregnancyException(PregnancyConstants.ERROR_RATE_LIMITED,
                "操作过于频繁，请稍后重试", 429);
    }
}
