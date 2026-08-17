package com.howe.ai.tool;

/**
 * Tool 调用审计条目：记录调用方、目标 Tool、裁决与原因。
 *
 * @param toolName      被调用的 Tool 名
 * @param agentId       发起调用的 Agent 标识（可为空）
 * @param userId        发起调用的用户标识（可为空）
 * @param allowed       是否放行执行
 * @param denyReason    拒绝原因；放行时为空
 * @param success       Tool 是否成功返回（拒绝时为 false）
 * @param errorMessage  Tool 失败时的错误信息；成功或拒绝时为空
 * @param timestamp     调用时间戳（毫秒）
 */
public record ToolAuditEntry(
    String toolName,
    String agentId,
    String userId,
    boolean allowed,
    String denyReason,
    boolean success,
    String errorMessage,
    long timestamp) {
}
