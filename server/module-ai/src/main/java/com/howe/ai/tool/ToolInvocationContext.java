package com.howe.ai.tool;

import java.util.Set;

/**
 * Tool 安全调用上下文：承载调用链每一关裁决所需的输入。
 *
 * <p>阶段一 Tool 全部 READ_ONLY，权限/数据范围已在 Controller 层由 {@code @PreAuthorize}
 * 把守；本上下文聚焦阶段一必须的服务端裁决：Agent 工具白名单与调用方身份（用于审计）。</p>
 *
 * @param toolName      目标 Tool 名
 * @param argumentsJson 模型产出的工具入参 JSON（仅用于透传，不参与白名单裁决）
 * @param allowedTools  Agent 发布版本固化的允许工具白名单；为空集合表示无任何授权
 * @param agentId       Agent 标识（审计用）
 * @param userId        用户标识（审计用）
 */
public record ToolInvocationContext(
    String toolName,
    String argumentsJson,
    Set<String> allowedTools,
    String agentId,
    String userId) {

    public ToolInvocationContext {
        if (toolName == null || toolName.isBlank()) {
            throw new IllegalArgumentException("toolName 不能为空");
        }
        allowedTools = allowedTools == null ? Set.of() : Set.copyOf(allowedTools);
        argumentsJson = argumentsJson == null ? "{}" : argumentsJson;
    }
}
