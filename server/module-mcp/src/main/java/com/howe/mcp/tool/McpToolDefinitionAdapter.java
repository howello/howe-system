package com.howe.mcp.tool;

import com.howe.common.tool.model.ToolDefinition;
import com.howe.common.tool.model.ToolInputSchema;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 通用工具定义到 MCP 协议包装的适配器。
 *
 * <p>这是 common 定义与 MCP 内部 {@link McpToolDefinition} 之间的唯一转换入口。
 * 转换只做结构映射：不补默认值、不把 null 转成空集合、不重排 properties、
 * 不丢弃无法表达的根关键字。</p>
 */
public final class McpToolDefinitionAdapter {

    /** 适配器不允许实例化。 */
    private McpToolDefinitionAdapter() {
    }

    /**
     * 把通用定义转换为 MCP 内部包装。
     *
     * @param definition 通用工具定义
     * @param resolvedScope 由能力分组解析出的 OAuth scope
     * @return MCP 内部工具定义
     */
    public static McpToolDefinition adapt(ToolDefinition definition, String resolvedScope) {
        Objects.requireNonNull(definition, "通用工具定义不能为空");
        if (resolvedScope == null || resolvedScope.isBlank()) {
            throw new IllegalArgumentException("工具缺少已解析的授权范围：" + definition.name());
        }
        McpSchema.Tool schema = schema(definition);
        return new McpToolDefinition(schema, definition.version(), definition.capabilityGroup(), resolvedScope,
                definition.access(), definition.timeout(), definition.maxResultBytes(), definition.validate(),
                (invocation, arguments) -> McpToolResultAdapter.toProtocol(
                        definition.handler().handle(context(invocation), arguments)));
    }

    /**
     * 构造协议工具描述。
     *
     * @param definition 通用工具定义
     * @return SDK 工具描述
     */
    static McpSchema.Tool schema(ToolDefinition definition) {
        ToolInputSchema input = definition.inputSchema();
        var annotations = definition.annotations();
        return McpSchema.Tool.builder()
                .name(definition.name())
                .title(definition.title())
                .description(definition.description())
                .inputSchema(new McpSchema.JsonSchema(input.type(), properties(input), input.required(),
                        input.additionalProperties(), input.defs(), input.definitions()))
                .annotations(new McpSchema.ToolAnnotations(annotations.title(), annotations.readOnlyHint(),
                        annotations.destructiveHint(), annotations.idempotentHint(), annotations.openWorldHint(),
                        annotations.returnDirect()))
                .build();
    }

    /**
     * 提取输入结构中的属性树，保持原有顺序。
     *
     * @param input 输入结构
     * @return 属性树；源为 null 时返回空 Map
     */
    private static Map<String, Object> properties(ToolInputSchema input) {
        Map<String, Object> source = input.properties();
        return source == null ? Map.of() : new LinkedHashMap<>(source);
    }

    /**
     * 从调用上下文构造最小业务上下文。
     *
     * <p>userId 必须取自平台重新加载的当前账号，不能沿用旧调用上下文中的部门快照。
     * 本方法只读取认证后已装入上下文的值。</p>
     *
     * @param invocation MCP 调用上下文
     * @return 通用执行上下文
     */
    private static com.howe.common.tool.model.ToolExecutionContext context(
            com.howe.mcp.service.security.McpInvocationContext invocation) {
        String userId = invocation == null || invocation.userId() == null
                ? null : String.valueOf(invocation.userId());
        String requestId = invocation == null ? null : invocation.requestId();
        return new com.howe.common.tool.model.ToolExecutionContext(userId, currentDeptId(), requestId);
    }

    /**
     * 读取当前绑定账号的部门 ID。
     *
     * <p>使用执行线程中已重新加载并绑定的 {@code LoginUser}，不读取旧上下文快照。</p>
     *
     * @return 部门 ID；无账号或无部门时返回 null
     */
    private static String currentDeptId() {
        try {
            var loginUser = com.howe.common.utils.SecurityUtils.getLoginUser();
            if (loginUser == null || loginUser.getDeptId() == null) {
                return null;
            }
            return String.valueOf(loginUser.getDeptId());
        } catch (RuntimeException exception) {
            return null;
        }
    }

    /**
     * 返回列表中可能出现的工具名，便于装配期校验。
     *
     * @param definitions 通用定义集合
     * @return 工具名列表
     */
    public static List<String> names(List<ToolDefinition> definitions) {
        return definitions.stream().map(ToolDefinition::name).toList();
    }
}
