package com.howe.mcp.operation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.howe.common.tool.exception.ToolException;
import com.howe.common.tool.model.ToolAccess;
import com.howe.common.tool.model.ToolExecutionContext;
import com.howe.common.tool.operation.ToolOperationStepContext;
import com.howe.common.tool.operation.ToolOperationStepHandler;
import com.howe.mcp.service.security.McpExecutionIdentity;
import com.howe.mcp.service.security.McpInvocationContext;
import com.howe.mcp.service.spi.McpToolExecutionBridge;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;

/**
 * 后续步骤执行器。
 *
 * <p>只执行父操作成功事务内登记的固定代码键步骤；领取带租约与围栏，
 * 执行前重新加载账号、原授权与工具开关。外部调用前必须落阶段，
 * 已有发送阶段但无回执时保持 UNKNOWN，不自动重发。</p>
 */
@Slf4j
public final class McpOperationStepExecutor {

    /** 步骤租约。 */
    private static final Duration LEASE = Duration.ofSeconds(30);

    /** 心跳间隔。 */
    private static final Duration HEARTBEAT = Duration.ofSeconds(10);

    /** 单轮领取上限。 */
    private static final int BATCH = 2;

    /** 步骤未知结果的最大尝试次数。 */
    private static final int MAX_STEP_ATTEMPTS = 5;

    private final McpOperationStepStore steps;

    private final McpOperationExecutionLimiter limiter;

    private final McpOperationStore operations;

    private final McpOperationService operationService;

    private final McpOperationBindingService bindings;

    private final McpExecutionIdentity identity;

    private final McpToolExecutionBridge executionBridge;

    private final com.howe.mcp.service.McpToolPolicyService policy;

    private final Map<String, ToolOperationStepHandler> handlers;

    private final ObjectMapper mapper;

    private final org.springframework.transaction.support.TransactionTemplate transactions;

    private final ThreadPoolExecutor pool;

    private final ScheduledExecutorService heartbeat;

    private final AtomicBoolean closed = new AtomicBoolean();

    private final String owner = "mcp-step-" + java.util.UUID.randomUUID();

    /**
     * 注入步骤执行依赖。
     *
     * @param steps 步骤账本
     * @param operations 父操作账本
     * @param operationService 操作服务，提供工具访问要求索引
     * @param bindings 授权绑定解析
     * @param identity 账号身份加载
     * @param executionBridge 业务权限桥接
     * @param policy 工具访问策略
     * @param handlers 步骤代码键到处理器的不可变映射
     * @param mapper JSON 序列化
     * @param concurrency 步骤执行并发
     */
    public McpOperationStepExecutor(McpOperationStepStore steps, McpOperationStore operations,
            McpOperationService operationService, McpOperationBindingService bindings,
            McpExecutionIdentity identity, McpToolExecutionBridge executionBridge,
            com.howe.mcp.service.McpToolPolicyService policy,
            Map<String, ToolOperationStepHandler> handlers, ObjectMapper mapper, int concurrency,
            org.springframework.transaction.support.TransactionTemplate transactions,
            McpOperationExecutionLimiter limiter) {
        this.steps = Objects.requireNonNull(steps);
        this.limiter = Objects.requireNonNull(limiter);
        this.operations = Objects.requireNonNull(operations);
        this.operationService = Objects.requireNonNull(operationService);
        this.bindings = Objects.requireNonNull(bindings);
        this.identity = Objects.requireNonNull(identity);
        this.executionBridge = Objects.requireNonNull(executionBridge);
        this.policy = Objects.requireNonNull(policy);
        this.handlers = Map.copyOf(handlers);
        this.mapper = Objects.requireNonNull(mapper);
        this.transactions = Objects.requireNonNull(transactions);
        this.pool = new ThreadPoolExecutor(concurrency, concurrency, 0, TimeUnit.SECONDS,
                new java.util.concurrent.ArrayBlockingQueue<>(concurrency), task -> {
                    Thread thread = new Thread(task, "mcp-step");
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
        this.heartbeat = new ScheduledThreadPoolExecutor(1, task -> {
            Thread thread = new Thread(task, "mcp-step-heartbeat");
            thread.setDaemon(true);
            return thread;
        });
    }

    /** 启动心跳。 */
    public void start() {
        heartbeat.scheduleWithFixedDelay(this::heartbeatOnce, HEARTBEAT.toMillis(), HEARTBEAT.toMillis(),
                TimeUnit.MILLISECONDS);
    }

    /**
     * 执行一轮有界领取。
     *
     * @return 本轮投递数量
     */
    public int dispatchOnce() {
        if (closed.get()) {
            return 0;
        }
        int submitted = 0;
        for (int i = 0; i < BATCH; i++) {
            Instant now = Instant.now();
            Optional<McpOperationStepRecord> claimed = steps.claimNext(owner, now, now.plus(LEASE));
            if (claimed.isEmpty()) {
                break;
            }
            McpOperationStepRecord record = claimed.get();
            if (record.executionDeadlineAt() != null && record.executionDeadlineAt().isBefore(now)) {
                finishQuietly(record, "FAILED", "EXPIRED", "task_deadline_exceeded");
                continue;
            }
            try {
                pool.execute(() -> run(record));
                submitted++;
            } catch (java.util.concurrent.RejectedExecutionException exception) {
                steps.defer(record.stepId(), owner, record.fence(), "QUEUED", "queue_full", Instant.now().plusSeconds(5));
            }
        }
        return submitted;
    }

    /** 执行单条步骤。 */
    private void run(McpOperationStepRecord record) {
        ToolOperationStepHandler handler = handlers.get(record.stepKey());
        if (handler == null) {
            finishQuietly(record, "FAILED", record.stage(), "step_handler_missing");
            return;
        }
        if (!Objects.equals(handler.stepVersion(), record.handlerVersion())) {
            finishQuietly(record, "FAILED", record.stage(), "step_version_changed");
            return;
        }
        if ("SEND_INTENT".equals(record.stage())) {
            steps.defer(record.stepId(), owner, record.fence(), "UNKNOWN", "audit_outcome_unknown",
                    Instant.now().plusSeconds(300));
            return;
        }
        McpOperationExecutionLimiter.Permit permit = null;
        try {
            Optional<McpOperationRecord> parent = operations.findById(record.operationId());
            if (parent.isEmpty()) {
                finishQuietly(record, "ABORTED", record.stage(), "parent_missing");
                return;
            }
            McpOperationRecord operation = parent.get();
            permit = limiter.tryAcquire(operation.userId(), "MEAL_AUDIT");
            if (permit == null) {
                steps.defer(record.stepId(), owner, record.fence(), "QUEUED", "server_busy",
                        Instant.now().plusSeconds(5));
                return;
            }
            McpOperationBinding binding = bindings.dispatchBinding(operation);
            bindings.requireToolEnabled(operation);
            // 步骤继承父工具的授权范围与业务权限，不能只凭父操作存在就继续执行。
            operationService.requireScope(operation, binding.scopes());
            McpInvocationContext invocation = new McpInvocationContext(operation.userId(), null,
                    operation.preparedDeptId(), bindings.externalClientId(operation), operation.grantId(),
                    operation.grantVersion(), binding.scopes(), operation.resourceUri(),
                    "step-" + record.stepId());
            Map<String, Object> payload = payload(record);
            identity.execute(invocation, () -> {
                ToolAccess access = access(operation);
                if (!policy.allowed(access)) {
                    throw new ToolException("execution_denied", "当前账号无权继续原操作");
                }
                executionBridge.execute(access, () -> {
                    ToolOperationStepContext.StepOutcome outcome = handler.execute(context(record, operation, invocation),
                            payload);
                    if (ownerAlive(record)) {
                        apply(record, outcome);
                    }
                    return null;
                });
                return null;
            });
        } catch (ToolException exception) {
            // 授权变化：立即终止，不再推进后续外部调用与业务写入。
            if ("authorization_changed".equals(exception.code())
                    || "execution_denied".equals(exception.code())
                    || "not_found_or_forbidden".equals(exception.code())) {
                finishQuietly(record, "ABORTED", record.stage(), exception.code());
                return;
            }
            finishQuietly(record, "FAILED", record.stage(),
                    exception.code() == null ? "internal_error" : exception.code());
        } catch (RuntimeException exception) {
            log.warn("MCP 后续步骤执行结果未知：stepId={}，类型={}", record.stepId(),
                    exception.getClass().getSimpleName());
            steps.defer(record.stepId(), owner, record.fence(), "UNKNOWN", "audit_outcome_unknown",
                    Instant.now().plusSeconds(300));
        } finally {
            if (permit != null) {
                permit.close();
            }
        }
    }

    /** 应用步骤结果。 */
    private void apply(McpOperationStepRecord record, ToolOperationStepContext.StepOutcome outcome) {
        if (outcome == null) {
            retryOrFail(record, record.stage() == null ? "EXECUTE" : record.stage(), "invalid_result");
            return;
        }
        String stage = record.stage() == null ? "EXECUTE" : record.stage();
        switch (outcome.kind()) {
            case APPLIED -> finish(record, "SUCCEEDED", stage, outcome.resultCode(), outcome.safeResult());
            case ABORTED -> finishQuietly(record, "ABORTED", stage, outcome.resultCode());
            case NOT_APPLIED -> finishQuietly(record, "FAILED", stage, outcome.resultCode());
            case UNKNOWN -> retryOrFail(record, stage, outcome.resultCode());
            default -> throw new IllegalStateException("未知步骤结果");
        }
    }

    /**
     * 结果未知时有限重试，超过上限转为失败终态。
     *
     * <p>提案自动审核的重试是安全的：领域写入与提案 CAS 在同一事务，
     * 已完成时第二次执行会因提案状态已变化而终止，不会重复创建菜品；
     * 但必须限制次数，避免持续消耗 AI 配额。</p>
     *
     * @param record 步骤
     * @param stage 当前阶段
     * @param resultCode 结果码
     */
    private void retryOrFail(McpOperationStepRecord record, String stage, String resultCode) {
        if (record.attempt() >= MAX_STEP_ATTEMPTS) {
            finishQuietly(record, "FAILED", stage,
                    resultCode == null ? "step_requires_manual_review" : resultCode);
            return;
        }
        long backoffSeconds = Math.min(300L, 30L << Math.min(4, record.attempt()));
        steps.defer(record.stepId(), owner, record.fence(), "UNKNOWN", resultCode,
                Instant.now().plusSeconds(backoffSeconds));
    }

    /** 写成功终态与加密结果。 */
    private void finish(McpOperationStepRecord record, String state, String stage, String resultCode,
            Map<String, Object> safeResult) {
        String json;
        try {
            json = mapper.writeValueAsString(safeResult);
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new ToolException("invalid_result", "步骤结果无法序列化");
        }
        if (!steps.finish(record.stepId(), owner, record.fence(), state, stage, resultCode, json)) {
            throw new ToolException("state_conflict", "步骤状态已被其它 worker 接管");
        }
    }

    /** 写终态；失败只记录，不打断其它步骤。 */
    private void finishQuietly(McpOperationStepRecord record, String state, String stage, String resultCode) {
        boolean changed = steps.finish(record.stepId(), owner, record.fence(), state, stage, resultCode, null);
        if (!changed) {
            log.warn("MCP 后续步骤终态写入未命中，stepId={}，目标状态={}", record.stepId(), state);
        }
    }

    /** 构造步骤上下文与授权守卫。 */
    private ToolOperationStepContext context(McpOperationStepRecord record, McpOperationRecord operation,
            McpInvocationContext invocation) {
        return new ToolOperationStepContext(new ToolExecutionContext(String.valueOf(operation.userId()),
                operation.preparedDeptId() == null ? null : String.valueOf(operation.preparedDeptId()),
                invocation.requestId()), operation.operationId(), record.stepId(), record.fence(), Instant.now(),
                record.executionDeadlineAt(), new ToolOperationStepContext.Guard() {
                    /** 每次调用都重新加载授权，并确认仍持有当前步骤租约。 */
                    @Override
                    public void check() {
                        McpOperationBinding current = bindings.binding(operation);
                        bindings.requireToolEnabled(operation);
                        operationService.requireScope(operation, current.scopes());
                        identity.execute(invocation, () -> {
                            if (!policy.allowed(access(operation))) {
                                throw new ToolException("authorization_changed", "当前账号权限已变化");
                            }
                            if (!Objects.equals(com.howe.common.utils.SecurityUtils.getDeptId(),
                                    operation.preparedDeptId())) {
                                throw new ToolException("authorization_changed", "当前账号家庭已变化");
                            }
                            return null;
                        });
                        if (!ownerAlive(record)) {
                            throw new ToolException("execution_denied", "步骤执行许可已失效");
                        }
                    }

                    /** 发送前原子冻结意图，未取得回执时禁止恢复重发。 */
                    @Override
                    public void externalSendIntent() {
                        transactions.executeWithoutResult(status -> {
                            check();
                            if (!steps.lockForExecution(record.stepId(), owner, record.fence())
                                    || !steps.updateStage(record.stepId(), owner, record.fence(), "SEND_INTENT")) {
                                throw new ToolException("execution_denied", "步骤围栏已失效");
                            }
                        });
                    }

                    /** 领域写入与步骤终态共用一个事务，失效围栏不能落业务。 */
                    @Override
                    public ToolOperationStepContext.StepOutcome commitAuthorized(
                            java.util.function.Supplier<ToolOperationStepContext.StepOutcome> action) {
                        return transactions.execute(status -> {
                            check();
                            if (!steps.lockForExecution(record.stepId(), owner, record.fence())) {
                                throw new ToolException("execution_denied", "步骤围栏已失效");
                            }
                            return identity.execute(invocation, () -> executionBridge.execute(access(operation), () -> {
                                ToolOperationStepContext.StepOutcome outcome = action.get();
                                apply(record, outcome);
                                return outcome;
                            }));
                        });
                    }
                });
    }

    /** 当前 worker 是否仍持有该步骤租约。 */
    private boolean ownerAlive(McpOperationStepRecord record) {
        return steps.findByOperation(record.operationId()).stream()
                .anyMatch(step -> step.stepId().equals(record.stepId())
                        && owner.equals(step.leaseOwner()) && step.fence() == record.fence()
                        && "RUNNING".equals(step.state()) && step.leaseUntil() != null
                        && step.leaseUntil().isAfter(Instant.now())
                        && (step.executionDeadlineAt() == null || step.executionDeadlineAt().isAfter(Instant.now())));
    }

    /** 读取并解析步骤载荷。 */
    private Map<String, Object> payload(McpOperationStepRecord record) {
        String json = steps.decryptedPayload(record.stepId());
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return mapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() { });
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new ToolException("invalid_result", "步骤载荷无法解析");
        }
    }

    /** 父工具的访问要求。 */
    private ToolAccess access(McpOperationRecord operation) {
        return operationService.accesses().get(operation.toolName() + "@" + operation.toolVersion());
    }

    /** 心跳续租全部持有的步骤。 */
    private void heartbeatOnce() {
        if (closed.get()) {
            return;
        }
        try {
            steps.heartbeatOwner(owner, Instant.now().plus(LEASE));
        } catch (RuntimeException exception) {
            log.warn("MCP 步骤心跳未完成，下轮继续");
        }
    }

    /** 关闭执行器。 */
    public void close() {
        closed.set(true);
        heartbeat.shutdownNow();
        pool.shutdown();
        try {
            pool.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
