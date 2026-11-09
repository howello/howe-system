package com.howe.mcp.tool;

import com.howe.mcp.service.security.McpScopes;
import io.modelcontextprotocol.spec.McpSchema;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 首期唯一的平台诊断工具，不访问业务数据、模型或外部服务。 */
public final class McpConnectionCheck {
    public static final String NAME = "mcp_connection_check";

    /** 工厂类不允许实例化。 */
    private McpConnectionCheck() {
    }

    /** 返回固定无参数契约与最小结果，不暴露用户、家庭和运行环境内部信息。 */
    public static McpToolDefinition definition() {
        var schema = McpSchema.Tool.builder().name(NAME).title("检查 MCP 连接")
                .description("检查当前授权连接和 MCP 服务状态，不访问业务数据。")
                .inputSchema(new McpSchema.JsonSchema("object", Map.of(), List.of(), false, null, null))
                .annotations(new McpSchema.ToolAnnotations("检查 MCP 连接", true, false, true, false, true)).build();
        return new McpToolDefinition(schema, "1", McpScopes.CONNECT, Set.of(), Duration.ofSeconds(5), 4096,
                arguments -> {
                    if (!arguments.isEmpty()) {
                        throw new IllegalArgumentException("连接检查不接受参数");
                    }
                }, (invocation, arguments) -> McpSchema.CallToolResult.builder().isError(false)
                        .content(List.of(new McpSchema.TextContent("MCP 授权连接正常")))
                        .structuredContent(Map.of("server", "howe-mcp", "version", "1",
                                "authenticated", true, "businessToolCount", 0)).build());
    }
}
