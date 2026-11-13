package com.howe.mcp.tool;

import com.howe.common.tool.model.ToolAccess;
import io.modelcontextprotocol.spec.McpSchema;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 平台诊断工具，不访问业务数据、模型或外部服务。
 *
 * <p>它不经过业务 Provider SPI，{@code resolvedScope} 由装配层传入与业务工具相同的
 * 映射解析入口，避免在此另硬编码一份分组到 scope 的映射。</p>
 */
public final class McpConnectionCheck {

    /** 工具名。 */
    public static final String NAME = "mcp_connection_check";

    /** 能力分组。 */
    public static final String CAPABILITY_GROUP = "mcp.connect";

    /** 工厂类不允许实例化。 */
    private McpConnectionCheck() {
    }

    /**
     * 返回固定无参数契约与最小结果，不暴露用户、家庭和运行环境内部信息。
     *
     * @param scopeResolver 能力分组到 OAuth scope 的解析入口
     * @return 平台工具定义
     */
    public static McpToolDefinition definition(Function<String, String> scopeResolver) {
        String resolvedScope = scopeResolver.apply(CAPABILITY_GROUP);
        var schema = McpSchema.Tool.builder().name(NAME).title("检查 MCP 连接")
                .description("检查当前授权连接和 MCP 服务状态，不访问业务数据。")
                .inputSchema(new McpSchema.JsonSchema("object", Map.of(), List.of(), false, null, null))
                .annotations(new McpSchema.ToolAnnotations("检查 MCP 连接", true, false, true, false, true)).build();
        return new McpToolDefinition(schema, "1", CAPABILITY_GROUP, resolvedScope, ToolAccess.empty(),
                Duration.ofSeconds(5), 4096,
                arguments -> {
                    if (arguments != null && !arguments.isEmpty()) {
                        throw new IllegalArgumentException("连接检查不接受参数");
                    }
                }, (invocation, arguments) -> McpSchema.CallToolResult.builder().isError(false)
                        .content(List.of(new McpSchema.TextContent("MCP 授权连接正常")))
                        .structuredContent(Map.of("server", "howe-mcp", "version", "1",
                                "authenticated", true, "businessToolCount", 0)).build());
    }
}
