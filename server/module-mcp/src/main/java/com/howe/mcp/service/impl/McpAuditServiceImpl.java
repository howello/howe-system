package com.howe.mcp.service.impl;

import com.howe.mcp.service.McpAuditService;
import com.howe.mcp.service.security.McpInvocationContext;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;

/** 将固定事件元数据写入应用日志。 */
@Slf4j
public final class McpAuditServiceImpl implements McpAuditService {
    /** 工具事件只记录固定结果和调用标识。 */
    @Override
    public void tool(McpInvocationContext invocation, String knownName, Result result, long elapsedMs) {
        record(new Event(invocation.requestId(), Kind.TOOL_CALL, invocation.userId(), null, invocation.grantId(),
                knownName, result, elapsedMs, Instant.now()));
    }

    /** 日志不携带凭据、业务参数或结果正文。 */
    @Override
    public void record(Event event) {
        log.info("MCP {} requestId={} userId={} client={} grant={} tool={} result={} elapsedMs={}",
                event.kind(), identifier(event.requestId()), event.userId(), identifier(event.registeredClientId()),
                event.grantId(), identifier(event.toolName()), event.result(), Math.max(0, event.elapsedMs()));
    }

    /** 标识使用有界的 ASCII 安全字符。 */
    private static String identifier(String value) {
        return value != null && value.matches("[A-Za-z0-9_-]{1,100}") ? value : null;
    }
}
