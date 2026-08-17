package com.howe.ai.tool;

/**
 * Tool 调用审计记录器：成功与拒绝都要记，便于事后排查越权、滥用与故障归因。
 *
 * <p>阶段一提供日志实现；后续可替换为落 {@code ai_tool_call} 表的实现而不改调用方。</p>
 */
public interface ToolAuditRecorder {

    /**
     * 记录一次 Tool 调用的审计条目。
     *
     * @param entry 审计条目（非空）
     */
    void record(ToolAuditEntry entry);
}
