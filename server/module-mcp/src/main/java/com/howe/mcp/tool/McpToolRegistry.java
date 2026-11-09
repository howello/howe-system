package com.howe.mcp.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.howe.common.utils.SecurityUtils;
import com.howe.mcp.service.security.McpExecutionIdentity;
import com.howe.mcp.service.security.McpInvocationContext;
import com.howe.mcp.service.McpAuditService.Result;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Predicate;
import lombok.extern.slf4j.Slf4j;

/** 显式工具目录和统一执行入口；授权检查由持久化状态守卫提供，默认不自行信任历史目录。 */
@Slf4j
public final class McpToolRegistry implements AutoCloseable {
    private final Map<String, McpToolDefinition> definitions;
    private final McpExecutionIdentity identity;
    private final Consumer<McpInvocationContext> authorizationGuard;
    private final Predicate<String> enabled;
    private final ObjectMapper mapper;
    private final ThreadPoolExecutor workers;
    private final Semaphore slots;
    private final Observer observer;
    private final int concurrency;
    private final int userConcurrency;
    private final Map<Long, Integer> userSlots = new HashMap<>();
    private boolean closed;

    /**
     * 构造显式目录，不扫描其它模块的 ToolCallback。
     *
     * @param definitions 允许发布的代码定义
     * @param identity 当前账号身份加载器
     * @param authorizationGuard 当前平台、账号、客户端、授权版本及资源状态复核
     * @param enabled 当前工具开放状态，缺失配置应返回 false
     * @param mapper 结果序列化器
     * @param concurrency 最大执行并发，不提供无界等待队列
     * @param userConcurrency 同一已认证用户跨客户端、授权和会话共享的最大执行并发
     * @param observer 工具结果观察器，只接收固定元数据，不接收参数或结果正文
     */
    public McpToolRegistry(Collection<McpToolDefinition> definitions, McpExecutionIdentity identity,
            Consumer<McpInvocationContext> authorizationGuard, Predicate<String> enabled,
            ObjectMapper mapper, int concurrency, int userConcurrency, Observer observer) {
        if (concurrency < 1 || concurrency > 64) {
            throw new IllegalArgumentException("工具执行并发必须在 1 到 64 之间");
        }
        if (userConcurrency < 1 || userConcurrency > 64) { throw new IllegalArgumentException("用户工具并发必须在 1 到 64 之间"); }
        Map<String, McpToolDefinition> indexed = new LinkedHashMap<>();
        for (McpToolDefinition definition : definitions) {
            if (indexed.putIfAbsent(definition.schema().name(), definition) != null) {
                throw new IllegalArgumentException("工具名称重复");
            }
        }
        this.definitions = Map.copyOf(indexed);
        this.identity = Objects.requireNonNull(identity);
        this.authorizationGuard = Objects.requireNonNull(authorizationGuard);
        this.enabled = Objects.requireNonNull(enabled);
        this.mapper = Objects.requireNonNull(mapper);
        this.observer = Objects.requireNonNull(observer);
        this.concurrency = concurrency;
        this.userConcurrency = userConcurrency;
        this.slots = new Semaphore(concurrency);
        this.workers = new ThreadPoolExecutor(concurrency, concurrency, 0, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(concurrency), task -> {
                    Thread thread = new Thread(task, "mcp-tool");
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
    }

    /** 后台只读取代码定义，不把执行器或回调暴露到 HTTP 响应。 */
    public List<McpToolDefinition> catalog() {
        return definitions.values().stream().sorted(java.util.Comparator.comparing(value -> value.schema().name())).toList();
    }

    /** 使用当前账号与授权筛选目录；此结果不能代替执行时检查。 */
    public List<McpSchema.Tool> visibleTools(McpInvocationContext invocation) {
        return identity.execute(invocation, () -> {
            authorizationGuard.accept(invocation);
            return definitions.values().stream().filter(tool -> allowed(invocation, tool))
                    .map(McpToolDefinition::schema).sorted(java.util.Comparator.comparing(McpSchema.Tool::name)).toList();
        });
    }

    /** 将已筛选目录适配成标准 SDK 工具定义，实际执行从每个请求的上下文重新取身份。 */
    public List<McpServerFeatures.SyncToolSpecification> specifications(McpInvocationContext invocation) {
        return visibleTools(invocation).stream().map(tool -> McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool).callHandler((exchange, request) -> {
                    Object value = exchange.transportContext().get(McpExecutionIdentity.TRANSPORT_KEY);
                    if (!(value instanceof McpInvocationContext current)) {
                        return failure("identity_missing");
                    }
                    return execute(current, tool.name(), request.arguments());
                }).build()).toList();
    }

    /** 独立检查调用目标、身份、权限和状态；失败时返回标准工具错误，不泄露原异常。 */
    public McpSchema.CallToolResult execute(McpInvocationContext invocation, String name, Map<String, Object> arguments) {
        long started = System.nanoTime();
        Result outcome = Result.EXECUTION_DENIED;
        try {
            McpSchema.CallToolResult result = executeOne(invocation, name, arguments);
            if (Boolean.TRUE.equals(result.isError())) {
                Object code = result.structuredContent() instanceof Map<?, ?> structured ? structured.get("error") : null;
                outcome = code instanceof String value ? Result.fromCode(value) : Result.TOOL_ERROR;
            } else { outcome = Result.OK; }
            return result;
        } finally {
            try {
                observer.completed(invocation, definitions.containsKey(name) ? name : null, outcome,
                        Math.max(0, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)));
            } catch (RuntimeException exception) { log.warn("MCP 工具审计提交失败"); }
        }
    }

    /** 工具实际执行与容量管理保持独立，观察器不会改变成功、拒绝或超时结果。 */
    private McpSchema.CallToolResult executeOne(McpInvocationContext invocation, String name, Map<String, Object> arguments) {
        McpToolDefinition definition = definitions.get(name);
        if (definition == null) {
            return failure("tool_unavailable");
        }
        String rejected = acquire(invocation == null ? null : invocation.userId());
        if (rejected != null) { return failure(rejected); }
        FutureTask<McpSchema.CallToolResult> future = task(invocation.userId(), () -> identity.execute(invocation, () -> {
            authorizationGuard.accept(invocation);
            if (!allowed(invocation, definition)) {
                return failure("tool_unavailable");
            }
            Map<String, Object> input;
            try {
                input = arguments == null ? Map.of() : Map.copyOf(arguments);
                definition.validate().accept(input);
            } catch (IllegalArgumentException | NullPointerException exception) {
                return failure("invalid_arguments");
            }
            McpSchema.CallToolResult result = definition.handler().apply(invocation, input);
            try {
                return result != null && mapper.writeValueAsBytes(result).length <= definition.maxResultBytes()
                        ? result : failure("result_too_large");
            } catch (JsonProcessingException exception) {
                return failure("invalid_result");
            }
        }));
        try {
            workers.execute(future);
        } catch (RejectedExecutionException exception) {
            future.cancel(false);
            return failure("server_busy");
        }
        try {
            return future.get(definition.timeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            future.cancel(true);
            workers.remove(future);
            return failure("tool_timeout");
        } catch (InterruptedException exception) {
            future.cancel(true);
            workers.remove(future);
            Thread.currentThread().interrupt();
            return failure("request_cancelled");
        } catch (CancellationException exception) {
            return failure("request_cancelled");
        } catch (ExecutionException exception) {
            log.warn("MCP 工具执行失败：tool={}，requestId={}", name, invocation.requestId());
            return failure("execution_denied");
        }
    }

    /**
     * 将许可绑定到实际任务体：正常返回前释放；运行中取消则等任务体退出后才释放。
     * 取消早于任务开始时，CAS 阻止后来进入任务体，避免许可泄漏或并发超限。
     */
    private FutureTask<McpSchema.CallToolResult> task(Long userId, Callable<McpSchema.CallToolResult> action) {
        AtomicInteger state = new AtomicInteger();
        return new FutureTask<>(() -> {
            if (!state.compareAndSet(0, 1)) {
                throw new CancellationException();
            }
            try {
                return action.call();
            } finally {
                state.set(2);
                release(userId);
            }
        }) {
            /** 未开始的取消任务直接归还许可，已经运行的任务由 finally 归还。 */
            @Override
            protected void done() {
                if (state.compareAndSet(0, 2)) {
                    release(userId);
                }
            }
        };
    }

    /** 用户计数和全局许可一起受锁保护，拒绝时不占用任意一方的容量。 */
    private synchronized String acquire(Long userId) {
        if (userId == null || userId <= 0) { return "identity_missing"; }
        if (closed) { return "server_busy"; }
        if (userSlots.getOrDefault(userId, 0) >= userConcurrency) { return "user_busy"; }
        if (!slots.tryAcquire()) { return "server_busy"; }
        userSlots.merge(userId, 1, Integer::sum);
        return null;
    }

    /** 许可随真实任务退出归还；零计数删除，用户键总量不超过全局执行容量。 */
    private synchronized void release(Long userId) {
        Integer count = userSlots.get(userId);
        if (count == null || count < 1) { throw new IllegalStateException("工具执行容量状态不一致"); }
        if (count == 1) { userSlots.remove(userId); }
        else { userSlots.put(userId, count - 1); }
        slots.release();
    }

    /** 只提供容量指标，不向监控接口暴露用户 ID 或工具参数。 */
    public synchronized ExecutionStatistics statistics() {
        return new ExecutionStatistics(concurrency, userConcurrency, concurrency - slots.availablePermits(), userSlots.size(), closed);
    }

    /** 并发上限按当前进程统计；共享 Redis 请求频率由协议入口独立控制。 */
    public record ExecutionStatistics(int limit, int perUserLimit, int running, int activeUsers, boolean closed) { }

    /** 同时检查工具开关、获授 scope 与当前账号业务权限。 */
    private boolean allowed(McpInvocationContext invocation, McpToolDefinition definition) {
        var permissions = SecurityUtils.getLoginUser().getPermissions();
        return enabled.test(definition.schema().name()) && invocation.scopes().contains(definition.scope())
                && permissions != null && (permissions.contains("*:*:*") || permissions.containsAll(definition.permissions()));
    }

    /** 统一输出稳定的工具错误，不包含参数、凭据、堆栈或内部异常消息。 */
    private static McpSchema.CallToolResult failure(String code) {
        return McpSchema.CallToolResult.builder().isError(true)
                .content(List.of(new McpSchema.TextContent("工具调用未完成：" + code)))
                .structuredContent(Map.of("error", code)).build();
    }

    /** 审计契约禁止接收原始参数、结果文本和异常。 */
    @FunctionalInterface
    public interface Observer {
        /** 每个调用至多报告一次客户端可见的结果，超时不等同于业务动作已回滚。 */
        void completed(McpInvocationContext invocation, String knownName, Result result, long elapsedMs);
    }

    /** 停止接收新任务并中断现有任务；不对工具自动重试。 */
    @Override
    public void close() {
        synchronized (this) { closed = true; }
        workers.shutdownNow().forEach(task -> {
            if (task instanceof FutureTask<?> future) {
                future.cancel(false);
            }
        });
    }
}
