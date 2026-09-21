package com.howe.ai.api;

/**
 * AI 调用错误码
 *
 * <p>业务模块只需 {@code catch (AiException e)} 然后按 {@code e.getCode()} 分支。
 * 错误码决定后续行为：可降级的错误会先尝试备用模型，不可降级的错误直接抛给调用方。</p>
 *
 * @author howe
 */
public enum AiErrorCode
{
    /** 总开关关闭 */
    AI_DISABLED("AI 能力未启用"),

    /** 场景未配路由且无全局默认 */
    NO_AVAILABLE_MODEL("没有可用的模型"),

    /** 指定的渠道/模型被停用 */
    CHANNEL_DISABLED("指定的渠道或模型已停用"),

    /** 密钥无效 */
    AUTH_FAILED("模型服务鉴权失败"),

    /** 限流 */
    RATE_LIMITED("模型服务触发限流"),

    /** 余额/配额不足 */
    QUOTA_EXCEEDED("模型服务余额或配额不足"),

    /** 内容审核不通过（透传厂商原因） */
    CONTENT_BLOCKED("内容未通过审核"),

    /** 超时 */
    TIMEOUT("模型服务响应超时"),

    /** 厂商 5xx / 协议错误 */
    PROVIDER_ERROR("模型服务返回异常"),

    /** 参数不合法（尺寸、张数、空 prompt） */
    BAD_REQUEST("请求参数不合法"),

    /** 生成成功但转存图床失败 */
    TRANSFER_FAILED("图片已生成但保存失败，请重试"),

    /** 降级链全部失败 */
    ALL_FALLBACK_FAILED("所有可用模型均调用失败");

    /** 面向用户的中文提示，后台管理端可直接弹出 */
    private final String message;

    AiErrorCode(String message)
    {
        this.message = message;
    }

    /**
     * 获取该错误码对应的默认提示语
     *
     * @return 中文提示
     */
    public String getMessage()
    {
        return message;
    }
}
