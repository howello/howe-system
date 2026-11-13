package com.howe.mcp.operation;

import com.howe.common.tool.model.ToolAccess;
import com.howe.common.tool.model.ToolDefinition;
import com.howe.common.tool.model.ToolInputSchema;
import com.howe.common.tool.operation.ToolOperationGateway;
import com.howe.common.tool.operation.ToolOperationReceipt;
import com.howe.common.tool.spi.BusinessToolProvider;
import com.howe.common.tool.support.ToolArguments;
import com.howe.common.tool.support.ToolResults;
import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 操作状态查询工具。
 *
 * <p>由平台注册，不接受业务 Provider 声明保留分组。输入只有 operationId；
 * 读取前仍按原工具权限与对象可见性复核，仅凭 {@code mcp:connect} 不足以读取历史结果。</p>
 */
public final class McpOperationGetToolProvider implements BusinessToolProvider {

    /** 保留的连接分组。 */
    private static final String GROUP = "mcp.connect";

    private final ToolOperationGateway gateway;

    /**
     * 构造查询工具提供者。
     *
     * @param gateway 操作网关
     */
    public McpOperationGetToolProvider(ToolOperationGateway gateway) {
        this.gateway = gateway;
    }

    @Override
    public String domain() {
        return "mcp";
    }

    @Override
    public Collection<ToolDefinition> definitions() {
        return List.of(operationGet());
    }

    /** 查询本人操作。 */
    private ToolDefinition operationGet() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("operationId", Map.of("type", "string", "pattern", "^[0-9a-f]{32}$"));
        return new ToolDefinition("mcp_operation_get", "1", "查询写操作状态",
                "按操作标识查询本人写操作的当前状态与安全结果；无法证明权限时不返回历史正文。",
                GROUP, new ToolInputSchema("object", properties, List.of("operationId"), false, null, null),
                ToolAccess.authenticated(),
                new ToolDefinition.ToolAnnotations("查询写操作状态", true, false, true, false, false),
                Duration.ofSeconds(5), 65536,
                arguments -> {
                    ToolArguments.rejectUnknown(arguments, Set.of("operationId"));
                    ToolArguments.requiredOperationId(arguments, "operationId");
                },
                (context, arguments) -> {
                    String operationId = ToolArguments.requiredOperationId(arguments, "operationId");
                    ToolOperationReceipt receipt = gateway.get(context, operationId);
                    Map<String, Object> data = new LinkedHashMap<>();
                    data.put("operationId", receipt.operationId());
                    data.put("state", receipt.state() == null ? null : receipt.state().name());
                    data.put("stage", receipt.stage());
                    data.put("createdAt", receipt.createdAt());
                    data.put("finishedAt", receipt.finishedAt());
                    data.put("resultCode", receipt.resultCode());
                    data.put("safeResult", receipt.safeResult());
                    data.put("retryAfterMs", receipt.retryAfterMs());
                    return ToolResults.detail(data, "已返回操作状态");
                });
    }
}
