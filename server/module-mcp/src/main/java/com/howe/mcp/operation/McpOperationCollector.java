package com.howe.mcp.operation;

import com.howe.common.tool.model.ToolAccess;
import com.howe.common.tool.operation.ToolOperationHandler;
import com.howe.common.tool.spi.BusinessToolProvider;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;

/**
 * 操作处理器与工具访问要求的延迟收集器。
 *
 * <p>在全部单例装配完成后统一索引，避免 {@code Provider → Gateway → OperationService →
 * Provider} 的构造期循环依赖。重复的工具键或 Handler 键直接启动失败，不静默覆盖。</p>
 */
public final class McpOperationCollector implements SmartInitializingSingleton {

    private final McpOperationService operations;

    private final ObjectProvider<ToolOperationHandler> handlers;

    private final ObjectProvider<BusinessToolProvider> providers;

    private final ObjectProvider<com.howe.common.tool.operation.ToolOperationStepHandler> stepHandlers;

    /**
     * 注入操作服务与两类提供者。
     *
     * @param operations 操作服务
     * @param handlers 全部操作处理器
     * @param providers 全部业务工具提供者
     * @param stepHandlers 全部后续步骤处理器
     */
    public McpOperationCollector(McpOperationService operations, ObjectProvider<ToolOperationHandler> handlers,
            ObjectProvider<BusinessToolProvider> providers,
            ObjectProvider<com.howe.common.tool.operation.ToolOperationStepHandler> stepHandlers) {
        this.operations = Objects.requireNonNull(operations);
        this.handlers = Objects.requireNonNull(handlers);
        this.providers = Objects.requireNonNull(providers);
        this.stepHandlers = Objects.requireNonNull(stepHandlers);
    }

    /** 全部单例就绪后索引 Handler、访问要求与步骤版本。 */
    @Override
    public void afterSingletonsInstantiated() {
        Map<String, ToolOperationHandler> indexed = new LinkedHashMap<>();
        handlers.orderedStream().forEach(handler -> {
            ToolOperationHandler.Key key = handler.key();
            String name = key.toolName() + "@" + key.toolVersion();
            if (indexed.putIfAbsent(name, handler) != null) {
                throw new IllegalStateException("操作处理器重复注册：" + name);
            }
        });
        Map<String, ToolAccess> accesses = new LinkedHashMap<>();
        providers.orderedStream().forEach(provider -> provider.definitions().forEach(definition ->
                accesses.put(definition.name() + "@" + definition.version(), definition.access())));
        Map<String, String> scopes = new LinkedHashMap<>();
        providers.orderedStream().forEach(provider -> provider.definitions().forEach(definition ->
                scopes.put(definition.name() + "@" + definition.version(),
                        com.howe.mcp.service.security.McpToolScopeBindings.resolve(definition.capabilityGroup()))));
        Map<String, String> stepVersions = new LinkedHashMap<>();
        stepHandlers.orderedStream().forEach(handler -> {
            if (stepVersions.putIfAbsent(handler.stepKey(), handler.stepVersion()) != null) {
                throw new IllegalStateException("后续步骤处理器重复注册：" + handler.stepKey());
            }
        });
        operations.scopes(scopes);
        operations.handlers(indexed);
        operations.accesses(accesses);
        operations.stepVersions(stepVersions);
    }

    /**
     * 返回后续步骤处理器索引，供步骤执行器装配使用。
     *
     * @return 步骤代码键到处理器的不可变映射
     */
    public Map<String, com.howe.common.tool.operation.ToolOperationStepHandler> stepHandlerIndex() {
        Map<String, com.howe.common.tool.operation.ToolOperationStepHandler> indexed = new LinkedHashMap<>();
        stepHandlers.orderedStream().forEach(handler -> {
            if (indexed.putIfAbsent(handler.stepKey(), handler) != null) {
                throw new IllegalStateException("后续步骤处理器重复注册：" + handler.stepKey());
            }
        });
        return Map.copyOf(indexed);
    }
}
