package com.howe.mcp.tool;

import com.howe.mcp.service.security.McpInvocationContext;
import io.modelcontextprotocol.spec.McpSchema;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/** 代码定义的工具契约；后台只能控制是否开放，不能修改实现或权限映射。 */
public record McpToolDefinition(McpSchema.Tool schema, String version, String scope, Set<String> permissions,
        Duration timeout, int maxResultBytes, Consumer<Map<String, Object>> validate,
        BiFunction<McpInvocationContext, Map<String, Object>, McpSchema.CallToolResult> handler) {

    /** 拒绝缺少名称、范围、参数校验或执行限制的工具。 */
    public McpToolDefinition {
        Objects.requireNonNull(schema);
        if (schema.name() == null || !schema.name().matches("[a-zA-Z0-9_-]{1,64}")
                || schema.inputSchema() == null || version == null || version.isBlank()
                || scope == null || scope.isBlank() || timeout == null || timeout.isNegative()
                || timeout.isZero() || timeout.compareTo(Duration.ofMinutes(1)) > 0 || maxResultBytes < 1) {
            throw new IllegalArgumentException("工具契约缺少必要的名称、版本、授权规则或执行限制");
        }
        permissions = Set.copyOf(permissions);
        Objects.requireNonNull(validate);
        Objects.requireNonNull(handler);
    }
}
