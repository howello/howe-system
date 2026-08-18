package com.howe.ai.tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 默认审计记录器：写日志。后续可替换为落 {@code ai_tool_call} 表的实现。
 *
 * <p>拒绝与成功都记 INFO 级，便于检索；失败明细见 {@link ToolAuditEntry#errorMessage()}。</p>
 */
@Component
public class LoggingToolAuditRecorder implements ToolAuditRecorder {

    private static final Logger log = LoggerFactory.getLogger("ai-tool-audit");

    @Override
    public void record(ToolAuditEntry entry) {
        if (entry.allowed()) {
            log.info("tool={} agent={} user={} allowed=true success={}",
                entry.toolName(), entry.agentId(), entry.userId(), entry.success());
        } else {
            log.info("tool={} agent={} user={} DENIED reason={}",
                entry.toolName(), entry.agentId(), entry.userId(), entry.denyReason());
        }
    }
}
