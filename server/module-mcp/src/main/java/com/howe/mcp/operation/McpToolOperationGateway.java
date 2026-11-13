package com.howe.mcp.operation;

import com.howe.common.tool.exception.ToolException;
import com.howe.common.tool.model.ToolExecutionContext;
import com.howe.common.tool.operation.ToolOperationGateway;
import com.howe.common.tool.operation.ToolOperationReceipt;
import com.howe.common.tool.operation.ToolOperationRequest;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 领域 Provider 可用的操作网关实现。
 *
 * <p>同步工具线程内由平台绑定当前 invocation 与已验证授权；本类只做可信作用域校验和
 * 服务转发，不接受请求参数中的身份、客户端或授权版本。异步执行不经过本类，
 * 由执行器从账本与数据库重新加载。</p>
 */
public final class McpToolOperationGateway implements ToolOperationGateway {

    private final McpOperationService operations;
    private final McpOperationBindingService bindings;
    private final com.howe.mcp.service.McpToolPolicyService policy;
    private final com.howe.mcp.service.spi.McpToolExecutionBridge bridge;
    private final ThreadLocal<Bound> binding = new ThreadLocal<>();

    /**
     * 注入操作服务与授权绑定解析。
     *
     * @param operations 操作服务
     * @param bindings 授权绑定解析
     */
    public McpToolOperationGateway(McpOperationService operations, McpOperationBindingService bindings,
            com.howe.mcp.service.McpToolPolicyService policy,
            com.howe.mcp.service.spi.McpToolExecutionBridge bridge) {
        this.operations = Objects.requireNonNull(operations);
        this.bindings = Objects.requireNonNull(bindings);
        this.policy = Objects.requireNonNull(policy);
        this.bridge = Objects.requireNonNull(bridge);
    }

    /**
     * 在当前工具线程绑定可信授权。
     *
     * @param record 已准备或已存在的账本记录；首次准备时可为 {@code null}
     * @param userId 当前账号
     * @param grantId 原授权
     */
    public void bind(Long userId, Long grantId) {
        binding.set(new Bound(userId, grantId));
    }

    /** 清理线程绑定。 */
    public void clear() {
        binding.remove();
    }

    /** 预览或准备。 */
    @Override
    public ToolOperationReceipt request(ToolExecutionContext context, ToolOperationRequest request) {
        Bound bound = required(context);
        McpOperationRecord record = current(bound);
        McpOperationBinding resolved = record == null
                ? bindings.currentBinding(bound.userId(), bound.grantId())
                : bindings.binding(record);
        return operations.request(request, context, resolved);
    }

    /** 结果查询：按账本内原工具绑定重新投影。 */
    @Override
    public ToolOperationReceipt get(ToolExecutionContext context, String operationId) {
        Bound bound = required(context);
        McpOperationRecord record = bindings.record(bound.userId(), operationId);
        if (!Objects.equals(bound.grantId(), record.grantId())) {
            throw new ToolException("not_found_or_forbidden", "操作不存在或无权访问");
        }
        McpOperationBinding resolved = bindings.binding(record);
        bindings.requireToolEnabled(record);
        operations.requireScope(record, resolved.scopes());
        String key = record.toolName() + "@" + record.toolVersion();
        var handler = operations.handlers().get(key);
        var access = operations.accesses().get(key);
        if (handler == null || access == null || !policy.allowed(access)) {
            throw new ToolException("execution_denied", "当前账号无权读取原工具结果");
        }
        return bridge.execute(access, () -> {
            var plan = operations.plan(record);
            handler.revalidate(new com.howe.common.tool.operation.ToolOperationContext(context,
                    com.howe.common.tool.operation.ToolOperationContext.Phase.RESULT_READ,
                    operationId, record.fence(), java.time.Instant.now(), null, () -> { }), plan);
            return new ToolOperationReceipt(record.operationId(), ToolOperationReceipt.State.valueOf(record.state()),
                    record.stage(), false, null, record.prepareExpiresAt(), record.createdAt(), record.finishedAt(),
                    record.resultCode(), plan.safeSummary(), operations.safeResult(record), null);
        });
    }

    /** 请求必须与线程绑定一致。 */
    private Bound required(ToolExecutionContext context) {
        Bound bound = binding.get();
        if (bound == null || context == null || !String.valueOf(bound.userId()).equals(context.userId())) {
            throw new ToolException("execution_denied", "操作上下文不可用");
        }
        return bound;
    }

    /** 同一幂等键下已存在的记录，用于复用原绑定。 */
    private McpOperationRecord current(Bound bound) {
        return bound.recordId() == null ? null : bindings.recordOrNull(bound.userId(), bound.recordId());
    }

    /** 类型安全转换。 */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> cast(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
    }

    /** 线程内可信绑定。 */
    private record Bound(Long userId, Long grantId, String recordId) {
        /** 简写构造。 */
        private Bound(Long userId, Long grantId) {
            this(userId, grantId, null);
        }
    }
}
