package com.howe.common.tool.operation;

import java.util.Map;

/**
 * 协议无关的操作申请。
 *
 * <p>只承载工具业务参数与幂等键；{@code toolName}/{@code toolVersion} 是代码常量，
 * 不从模型参数读取。{@code arguments} 只允许 JSON 基础类型，不包含身份、授权、
 * 确认凭证或任意脚本。</p>
 *
 * @param toolName 工具名，代码常量
 * @param toolVersion 工具版本，代码常量
 * @param mode 预览或准备
 * @param idempotencyKey 幂等键，16—64 位
 * @param arguments 规范化业务参数
 */
public record ToolOperationRequest(String toolName, String toolVersion, Mode mode, String idempotencyKey,
        Map<String, Object> arguments) {

    /** 预览不落库；准备仅建立待确认操作。 */
    public enum Mode {
        /** 只计算预览，不写账本。 */
        PREVIEW,
        /** 创建待确认操作。 */
        PREPARE
    }

    /** 深度复制业务参数，避免申请后被外部修改。 */
    public ToolOperationRequest {
        if (toolName == null || toolName.isBlank()) {
            throw new IllegalArgumentException("工具名不能为空");
        }
        if (toolVersion == null || toolVersion.isBlank()) {
            throw new IllegalArgumentException("工具版本不能为空");
        }
        if (mode == null) {
            throw new IllegalArgumentException("操作模式不能为空");
        }
        if (idempotencyKey == null || idempotencyKey.length() < 16 || idempotencyKey.length() > 64) {
            throw new IllegalArgumentException("幂等键长度不合法");
        }
        arguments = ToolOperationValues.copyMap(arguments);
    }
}
