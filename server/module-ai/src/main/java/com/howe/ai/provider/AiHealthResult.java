package com.howe.ai.provider;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 连通性检测结果
 *
 * @param success   是否连通
 * @param message   面向用户的说明
 * @param errorCode 失败时的 AI 错误码，成功时为 null
 * @author howe
 */
@Schema(description = "连通性检测结果")
public record AiHealthResult(boolean success, String message, String errorCode)
{
    /**
     * 构造成功结果
     *
     * @param message 说明
     * @return 检测结果
     */
    public static AiHealthResult ok(String message)
    {
        return new AiHealthResult(true, message, null);
    }

    /**
     * 构造失败结果
     *
     * @param message   说明
     * @param errorCode 错误码
     * @return 检测结果
     */
    public static AiHealthResult fail(String message, String errorCode)
    {
        return new AiHealthResult(false, message, errorCode);
    }
}
