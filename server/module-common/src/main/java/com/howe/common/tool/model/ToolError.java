package com.howe.common.tool.model;

/**
 * 工具错误对象。
 *
 * <p>只有受控错误码与固定中文提示会进入工具响应，不包含堆栈、SQL、上游报文或厂商错误。</p>
 *
 * @param code 稳定错误码，小写 snake_case
 * @param message 固定中文提示
 * @param retryable 是否允许客户端在不改变参数的前提下重试
 */
public record ToolError(String code, String message, boolean retryable) {
}
