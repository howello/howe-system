package com.howe.mcp.tool;

import com.howe.common.tool.model.ToolAccess;
import com.howe.mcp.service.security.McpInvocationContext;
import io.modelcontextprotocol.spec.McpSchema;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * MCP 内部的适配后工具定义。
 *
 * <p>由 {@link McpToolDefinitionAdapter} 从 common 的
 * {@link com.howe.common.tool.model.ToolDefinition} 转换而来，是注册器、策略、
 * 管理视图与连接检查共同消费的统一类型。本类型只存在于 MCP 内部，不向业务模块暴露。</p>
 *
 * @param schema 协议工具描述
 * @param version 工具版本
 * @param capabilityGroup 能力分组
 * @param resolvedScope 由能力分组解析出的 OAuth scope
 * @param access 通用业务访问要求
 * @param timeout 执行超时
 * @param maxResultBytes 结果字节上限
 * @param validate 参数校验器
 * @param handler 协议结果处理器
 */
public record McpToolDefinition(McpSchema.Tool schema, String version, String capabilityGroup, String resolvedScope,
        ToolAccess access, Duration timeout, int maxResultBytes, Consumer<Map<String, Object>> validate,
        BiFunction<McpInvocationContext, Map<String, Object>, McpSchema.CallToolResult> handler) {

    /** 拒绝缺少名称、版本、授权规则或执行限制的工具。 */
    public McpToolDefinition {
        Objects.requireNonNull(schema);
        if (schema.name() == null || !schema.name().matches("[a-zA-Z0-9_-]{1,64}")
                || schema.inputSchema() == null || version == null || version.isBlank()
                || resolvedScope == null || resolvedScope.isBlank() || timeout == null || timeout.isNegative()
                || timeout.isZero() || timeout.compareTo(Duration.ofMinutes(1)) > 0 || maxResultBytes < 1) {
            throw new IllegalArgumentException("工具契约缺少必要的名称、版本、授权规则或执行限制");
        }
        Objects.requireNonNull(access);
        Objects.requireNonNull(validate);
        Objects.requireNonNull(handler);
    }

    /**
     * 返回兼容管理接口的权限集合投影。
     *
     * @return {@code allOf} 权限集合
     */
    public java.util.Set<String> permissions() {
        return access.allOf();
    }
}
