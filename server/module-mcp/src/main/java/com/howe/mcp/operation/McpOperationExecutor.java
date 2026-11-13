package com.howe.mcp.operation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.howe.common.config.ConfigProvider;
import com.howe.common.constant.ConfigConstants;
import com.howe.common.tool.exception.ToolException;
import com.howe.common.tool.model.ToolAccess;
import com.howe.common.tool.model.ToolExecutionContext;
import com.howe.common.tool.operation.ToolOperationContext;
import com.howe.common.tool.operation.ToolOperationHandler;
import com.howe.common.tool.operation.ToolOperationHandler.Outcome;
import com.howe.common.tool.operation.ToolOperationHandler.OutcomeKind;
import com.howe.common.tool.operation.ToolOperationHandler.Plan;
import com.howe.common.tool.operation.ToolOperationHandler.QueueGroup;
import com.howe.mcp.service.security.McpExecutionIdentity;
import com.howe.mcp.service.security.McpInvocationContext;
import com.howe.mcp.service.spi.McpToolExecutionBridge;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;

/**
 * 操作执行器。
 *
 * <p>独立于同步工具池：按数据库租约领取 {@code QUEUED} 操作，在固定容量线程池中执行
 * 领域 Handler。发送外部效果前先持久化阶段；结果不明时保留 {@code UNKNOWN}，
 * 不盲重试、不释放占用。所有执行前材料都从账本与当前数据库重新加载，
 * 不继承确认线程的 ThreadLocal。</p>
 */
@Slf4j
public final class McpOperationExecutor {

    /** 租约时长。 */
    private static final Duration LEASE = Duration.ofSeconds(30);

    /** 心跳间隔。 */
    private static final Duration HEARTBEAT = Duration.ofSeconds(10);

    /** 单轮领取上限。 */
    private static final int BATCH = 4;

    private final McpOperationStore store;
    private final McpOperationService operations;
    private final McpOperationBindingService bindings;
    private final McpExecutionIdentity identity;
    private final McpToolExecutionBridge executionBridge;
    private final com.howe.mcp.service.McpToolPolicyService policy;
    private final ObjectMapper mapper;
    private final org.springframework.transaction.support.TransactionTemplate transactions;
    private final McpOperationStepStore stepStore;
    private final McpOperationExecutionLimiter limiter;
    private final ThreadPoolExecutor localPool;
    private final ThreadPoolExecutor publicationPool;
    private final ThreadPoolExecutor mealPool;
    private final ScheduledExecutorService heartbeat;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final String owner = "mcp-op-" + UUID.randomUUID();

    /**
     * 注入执行依赖并创建固定容量线程池。
     *
     * @param store 账本
     * @param operations 操作服务，运行期提供当前 Handler 索引
     * @param bindings 授权绑定解析
     * @param identity 账号身份加载
     * @param executionBridge 业务权限桥接
     * @param policy 工具访问策略
     * @param mapper JSON 序列化器
     * @param config 配置读取
     * @param transactions 事务模板
     * @param stepStore 后续步骤账本，用于父操作成功事务内登记步骤
     */
    public McpOperationExecutor(McpOperationStore store, McpOperationService operations,
            McpOperationBindingService bindings, McpExecutionIdentity identity,
            McpToolExecutionBridge executionBridge, com.howe.mcp.service.McpToolPolicyService policy,
            ObjectMapper mapper, ConfigProvider config,
            org.springframework.transaction.support.TransactionTemplate transactions,
            McpOperationStepStore stepStore) {
        this.store = Objects.requireNonNull(store);
        this.operations = Objects.requireNonNull(operations);
        this.bindings = Objects.requireNonNull(bindings);
        this.identity = Objects.requireNonNull(identity);
        this.executionBridge = Objects.requireNonNull(executionBridge);
        this.policy = Objects.requireNonNull(policy);
        this.mapper = Objects.requireNonNull(mapper);
        this.transactions = Objects.requireNonNull(transactions);
        this.stepStore = Objects.requireNonNull(stepStore);
        this.localPool = pool(parameter(config, ConfigConstants.MCP_OPERATION_LOCAL_CONCURRENCY, 4, 1, 32),
                "mcp-op-local");
        this.publicationPool = pool(parameter(config, ConfigConstants.MCP_OPERATION_PUBLISH_CONCURRENCY, 1, 1, 8),
                "mcp-op-publish");
        this.mealPool = pool(parameter(config, ConfigConstants.MCP_OPERATION_MEAL_CONCURRENCY, 2, 1, 8),
                "mcp-op-meal");
        // 进程内总/单账号/分组并发：只作为单进程容量控制，不宣称集群总容量。
        this.limiter = new McpOperationExecutionLimiter(
                parameter(config, ConfigConstants.MCP_OPERATION_TOTAL_CONCURRENCY, 4, 1, 32),
                parameter(config, ConfigConstants.MCP_OPERATION_USER_CONCURRENCY, 1, 1, 4),
                Map.of(QueueGroup.LOCAL_WRITE.name(),
                        parameter(config, ConfigConstants.MCP_OPERATION_LOCAL_CONCURRENCY, 4, 1, 32),
                        QueueGroup.BLOG_PUBLICATION.name(),
                        parameter(config, ConfigConstants.MCP_OPERATION_PUBLISH_CONCURRENCY, 1, 1, 8),
                        QueueGroup.MEAL_WRITE.name(),
                        parameter(config, ConfigConstants.MCP_OPERATION_MEAL_CONCURRENCY, 2, 1, 8)));
        this.heartbeat = new ScheduledThreadPoolExecutor(1, task -> {
            Thread thread = new Thread(task, "mcp-op-heartbeat");
            thread.setDaemon(true);
            return thread;
        });
    }

    /** 与后续步骤共享进程总量和账号执行许可。 */
    public McpOperationExecutionLimiter executionLimiter() {
        return limiter;
    }

    /** 启动执行器心跳。 */
    public void start() {
        heartbeat.scheduleWithFixedDelay(this::heartbeatOnce, HEARTBEAT.toMillis(), HEARTBEAT.toMillis(),
                TimeUnit.MILLISECONDS);
    }

    /**
     * 执行一轮有界派发，按队列分组选择线程池。
     *
     * @return 本轮成功投递的数量
     */
    public int dispatchOnce() {
        if (closed.get()) {
            return 0;
        }
        int submitted = 0;
        for (int i = 0; i < BATCH; i++) {
            Instant now = Instant.now();
            Optional<McpOperationRecord> claimed = store.claimNextQueued(owner, now, now.plus(LEASE));
            if (claimed.isEmpty()) {
                break;
            }
            McpOperationRecord record = claimed.get();
            if (expired(record, now)) {
                expire(record);
                continue;
            }
            ThreadPoolExecutor target;
            try {
                target = poolFor(record.queueGroup());
            } catch (com.howe.common.tool.exception.ToolException exception) {
                markFailed(record, "tool_unavailable");
                continue;
            }
            McpOperationExecutionLimiter.Permit permit = limiter.tryAcquire(record.userId(), record.queueGroup());
            if (permit == null) {
                // 没有执行许可：本轮不领取该条，放回队列稍后再试，避免队首占满。
                store.releaseToQueued(record.operationId(), owner, record.fence(), Instant.now().plusSeconds(2));
                continue;
            }
            try {
                target.execute(() -> {
                    try {
                        run(record);
                    } finally {
                        // 许可只在真正线程退出时归还，不会因租约到期提前释放。
                        permit.close();
                    }
                });
                submitted++;
            } catch (RejectedExecutionException exception) {
                // 未进入任务体：归还许可并按围栏放回队列，由下一轮重新领取。
                permit.close();
                store.releaseToQueued(record.operationId(), owner, record.fence(), Instant.now().plusSeconds(5));
            }
        }
        return submitted;
    }

    /**
     * 按队列分组选择执行池。
     *
     * <p>分组显式映射，未知分组不能被静默塞进本地池，否则并发隔离与恢复语义都会失真。</p>
     *
     * @param queueGroup 账本记录的分组
     * @return 对应线程池
     */
    private ThreadPoolExecutor poolFor(String queueGroup) {
        if (QueueGroup.BLOG_PUBLICATION.name().equals(queueGroup)) {
            return publicationPool;
        }
        if (QueueGroup.MEAL_WRITE.name().equals(queueGroup)) {
            return mealPool;
        }
        if (QueueGroup.LOCAL_WRITE.name().equals(queueGroup)) {
            return localPool;
        }
        throw new com.howe.common.tool.exception.ToolException("tool_unavailable",
                "不支持的执行队列分组：" + queueGroup);
    }

    /** 本地恢复复用派发的身份、权限及事务路径，处理器索引在执行时读取。 */
    public int recoverOnce() {
        if (closed.get()) {
            return 0;
        }
        int submitted = 0;
        for (int i = 0; i < BATCH; i++) {
            Instant now = Instant.now();
            Optional<McpOperationRecord> claimed = store.claimNextRecoverable(owner, now, now.plus(LEASE));
            if (claimed.isEmpty()) {
                break;
            }
            McpOperationRecord record = claimed.get();
            if (expired(record, now)) {
                expire(record);
                continue;
            }
            try {
                // 恢复同样按原分组选池，避免点餐写与其它写互相挤占。
                McpOperationExecutionLimiter.Permit permit = limiter.tryAcquire(record.userId(),
                        record.queueGroup());
                if (permit == null) {
                    store.deferRecovery(record.operationId(), owner, record.fence(), now.plusSeconds(2));
                    continue;
                }
                try {
                    poolFor(record.queueGroup()).execute(() -> {
                        try {
                            run(record);
                        } finally {
                            permit.close();
                        }
                    });
                    submitted++;
                } catch (RejectedExecutionException exception) {
                    permit.close();
                    store.deferRecovery(record.operationId(), owner, record.fence(), now.plusSeconds(5));
                }
            } catch (com.howe.common.tool.exception.ToolException exception) {
                // 未知分组：直接失败，不能静默塞进本地池。
                markFailed(record, "tool_unavailable");
            }
        }
        return submitted;
    }

    /** 执行单条已领取操作。 */
    private void run(McpOperationRecord record) {
        ToolOperationHandler handler = operations.handlers().get(record.toolName() + "@" + record.toolVersion());
        if (handler == null) {
            failOrDeferSent(record, "tool_unavailable");
            return;
        }
        try {
            if (!store.consumeConfirmation(record.operationId(), owner, record.fence(), Instant.now())) {
                failOrDeferSent(record, "confirmation_expired");
                return;
            }
            McpOperationBinding binding = bindings.dispatchBinding(record);
            bindings.requireToolEnabled(record);
            operations.requireScope(record, binding.scopes());
            Plan plan = operations.plan(record);
            executeWithIdentity(record, handler, binding, plan);
        } catch (ToolException exception) {
            handleToolException(record, exception);
        } catch (RuntimeException exception) {
            log.warn("MCP 操作执行结果未知：operationId={}，类型={}", record.operationId(),
                    exception.getClass().getSimpleName());
            markUnknown(record, "internal_error");
        }
    }

    /** 根据执行模型选择事务边界，并保证终态写入与领域完成同事务。 */
    private void executeWithIdentity(McpOperationRecord record, ToolOperationHandler handler,
            McpOperationBinding binding, Plan plan) {
        McpInvocationContext invocation = new McpInvocationContext(record.userId(), null, record.preparedDeptId(),
                bindings.externalClientId(record), record.grantId(), record.grantVersion(), binding.scopes(),
                record.resourceUri(), "dispatch-" + record.operationId());
        identity.execute(invocation, () -> {
            ToolAccess access = access(record, handler);
            if (!policy.allowed(access)) {
                throw new ToolException("execution_denied", "当前账号无权执行原工具");
            }
            executionBridge.execute(access, () -> {
                if (plan.executionKind() == ToolOperationHandler.ExecutionKind.LOCAL_TRANSACTION) {
                    runLocalTransaction(record, handler, plan, invocation);
                } else {
                    runRemoteReconcilable(record, handler, plan, invocation);
                }
                return null;
            });
            return null;
        });
    }

    /** 本地写：检查、执行、领域完成、账本终态与事件在同一短事务内。 */
    private void runLocalTransaction(McpOperationRecord record, ToolOperationHandler handler, Plan plan,
            McpInvocationContext invocation) {
        Boolean done = transactions.execute(status -> {
            if (!store.lockForExecution(record.operationId(), owner, record.fence())) {
                throw new ToolException("execution_denied", "执行许可已失效");
            }
            Outcome outcome = executeHandler(record, handler, plan, invocation);
            if (outcome == null || outcome.kind() == null || outcome.kind() == OutcomeKind.UNKNOWN) {
                throw new ToolException("operation_outcome_unknown", "本地事务结果未确认，已回滚", true);
            }
            if (outcome.kind() == OutcomeKind.NOT_APPLIED) {
                throw new ToolException(outcome.resultCode() == null ? "state_conflict" : outcome.resultCode(),
                        "本地操作未生效，事务已回滚");
            }
            completeAndSucceed(record, handler, plan, outcome, invocation);
            return Boolean.TRUE;
        });
        if (!Boolean.TRUE.equals(done)) {
            throw new ToolException("internal_error", "操作未完成");
        }
    }

    /**
     * 远端可对账：网络调用在事务外，最终本地完成与账本终态同事务。
     *
     * <p>已存在发送意图的重入不再调用 execute，交由恢复任务对账。</p>
     */
    private void runRemoteReconcilable(McpOperationRecord record, ToolOperationHandler handler, Plan plan,
            McpInvocationContext invocation) {
        // 一旦记录过外部发送意图，恢复只能查证既有结果，绝不能再次发送。
        Outcome outcome = hasSendIntent(record)
                ? handler.reconcile(context(record, invocation, Instant.now().plus(LEASE),
                        ToolOperationContext.Phase.RECONCILE), plan)
                : executeHandler(record, handler, plan, invocation);
        if (outcome == null || outcome.kind() == null) {
            markUnknown(record, "invalid_result");
            return;
        }
        if (outcome.kind() == OutcomeKind.UNKNOWN) {
            markUnknown(record, outcome.resultCode() == null ? "operation_outcome_unknown" : outcome.resultCode());
            return;
        }
        if (outcome.kind() == OutcomeKind.NOT_APPLIED) {
            transactions.executeWithoutResult(status -> {
                ToolOperationContext context = context(record, invocation, record.executionDeadlineAt());
                handler.releaseUnapplied(context, plan, outcome.resultCode());
                markFailed(record, outcome.resultCode() == null ? "state_conflict" : outcome.resultCode());
            });
            return;
        }
        // 远端已成功：只在最终短事务里补本地并写成功；失败则保留远端回执与 UNKNOWN。
        transactions.executeWithoutResult(status -> completeAndSucceed(record, handler, plan, outcome, invocation));
    }

    /** 执行一次领域处理器，统一构造可信上下文。 */
    private Outcome executeHandler(McpOperationRecord record, ToolOperationHandler handler, Plan plan,
            McpInvocationContext invocation) {
        ToolOperationContext context = context(record, invocation, Instant.now().plus(LEASE));
        if (plan.executionKind() == ToolOperationHandler.ExecutionKind.LOCAL_TRANSACTION) {
            context = context(record, invocation, record.executionDeadlineAt());
        }
        handler.revalidate(context, plan);
        return handler.execute(context, plan);
    }

    /** 构造带租约检查的领域上下文。 */
    private ToolOperationContext context(McpOperationRecord record, McpInvocationContext invocation,
            Instant deadline) {
        return context(record, invocation, deadline, ToolOperationContext.Phase.EXECUTE);
    }

    /** 对账上下文与执行上下文共用租约守卫，但向领域明确标明阶段。 */
    private ToolOperationContext context(McpOperationRecord record, McpInvocationContext invocation,
            Instant deadline, ToolOperationContext.Phase phase) {
        return new ToolOperationContext(new ToolExecutionContext(String.valueOf(record.userId()),
                record.preparedDeptId() == null ? null : String.valueOf(record.preparedDeptId()),
                invocation.requestId()), phase, record.operationId(), record.fence(),
                Instant.now(), deadline, new ToolOperationContext.LeaseGuard() {
                    /** 定期续租检查。 */
                    @Override
                    public void check() {
                        if (!store.heartbeat(record.operationId(), owner, record.fence(), Instant.now().plus(LEASE))) {
                            throw new ToolException("execution_denied", "执行许可已失效");
                        }
                    }

                    @Override
                    public void checkpointExternalSendIntent() {
                        Boolean checkpointed = transactions.execute(status ->
                                store.checkpointExternalSendIntent(record.operationId(), owner, record.fence(),
                                        "REMOTE_SEND_INTENT")
                                        ? Boolean.TRUE : Boolean.FALSE);
                        if (!Boolean.TRUE.equals(checkpointed)) {
                            throw new ToolException("execution_denied", "发送许可已失效");
                        }
                    }

                    @Override
                    public void checkpointRemoteCommitted(Runnable persistDomainReceipt) {
                        Boolean done = transactions.execute(status -> {
                            check();
                            persistDomainReceipt.run();
                            if (!store.markRemoteCommittedStage(record.operationId(), owner, record.fence())) {
                                throw new ToolException("execution_denied", "发送租约已失效");
                            }
                            return Boolean.TRUE;
                        });
                        if (!Boolean.TRUE.equals(done)) {
                            throw new ToolException("execution_denied", "发送租约已失效");
                        }
                    }

                    @Override
                    public void inFenceTransaction(Runnable action) {
                        Boolean done = transactions.execute(status -> {
                            check();
                            action.run();
                            return Boolean.TRUE;
                        });
                        if (!Boolean.TRUE.equals(done)) {
                            throw new ToolException("execution_denied", "恢复租约已失效");
                        }
                    }

                    @Override
                    public void checkpointReconciledRemoteCommitted(Runnable persistDomainReceipt) {
                        Boolean done = transactions.execute(status -> {
                            check();
                            persistDomainReceipt.run();
                            if (!store.reconcileRemoteCommittedStage(record.operationId(), owner, record.fence())) {
                                throw new ToolException("execution_denied", "恢复租约已失效");
                            }
                            return Boolean.TRUE;
                        });
                        if (!Boolean.TRUE.equals(done)) {
                            throw new ToolException("execution_denied", "恢复租约已失效");
                        }
                    }
                });
    }

    /** 领域完成 + 账本成功 + 事件；任一失败由外层事务回滚。 */
    private void completeAndSucceed(McpOperationRecord record, ToolOperationHandler handler, Plan plan, Outcome outcome,
            McpInvocationContext invocation) {
        Instant deadline = ToolOperationHandler.ExecutionKind.REMOTE_RECONCILABLE.name().equals(record.executionKind())
                && hasSendIntent(record) ? Instant.now().plus(LEASE) : record.executionDeadlineAt();
        ToolOperationContext context = context(record, invocation, deadline);
        Map<String, Object> result = handler.complete(context, plan, outcome);
        String encrypted;
        try {
            encrypted = result == null ? null : mapper.writeValueAsString(result);
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new ToolException("invalid_result", "工具结果无法序列化");
        }
        // 父操作成功与后续步骤登记必须同事务：父操作已成功却没有步骤是不可恢复的半成品。
        persistContinuations(record, handler, context, plan, outcome);
        boolean changed = store.transition(record.operationId(), record.fence(), McpOperationState.RUNNING,
                McpOperationState.SUCCEEDED, "LOCAL_COMMITTED", outcome.resultCode(), encrypted, "EXECUTED", "worker",
                owner);
        if (!changed) {
            throw new ToolException("state_conflict", "操作终态已被其它 worker 接管");
        }
    }

    /**
     * 在父操作成功事务内登记领域声明的后续步骤。
     *
     * <p>重复代码键不重复插入；已完成步骤不会被新声明覆盖。步骤截止时间取父操作执行截止时间，
     * 避免父操作结束后步骤无限期挂起。</p>
     *
     * @param context 父操作上下文
     * @param plan 计划
     * @param outcome 执行结果
     * @param handler 当前领域处理器
     */
    private void persistContinuations(McpOperationRecord record, ToolOperationHandler handler,
            ToolOperationContext context, Plan plan, Outcome outcome) {
        for (com.howe.common.tool.operation.ToolOperationContinuation continuation
                : handler.continuations(context, plan, outcome)) {
            String encryptedPayload;
            try {
                encryptedPayload = mapper.writeValueAsString(continuation.payload());
            } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
                throw new ToolException("invalid_result", "后续步骤载荷无法序列化");
            }
            String stepId = McpOperationStoreImpl.newOperationId();
            Instant deadline = context.executionDeadlineAt() == null
                    ? Instant.now().plus(Duration.ofMinutes(10)) : context.executionDeadlineAt();
            stepStore.insert(stepId, record.operationId(), continuation.stepKey(),
                    operations.stepVersions().getOrDefault(continuation.stepKey(), "1"),
                    encryptedPayload, deadline, Instant.now());
        }
    }

    /** 确定失败终态；写入失败必须抛错，不能把未落库当成功。 */
    private void markFailed(McpOperationRecord record, String code) {
        boolean changed = store.transition(record.operationId(), record.fence(), McpOperationState.RUNNING,
                McpOperationState.FAILED, "FAILED", code, null, "EXECUTED", "worker", owner);
        if (!changed) {
            throw new ToolException("state_conflict", "操作终态已被其它 worker 接管");
        }
    }

    /** 已进入远端发送阶段时保留未决事实，不能宣称外部写入失败。 */
    private void failOrDeferSent(McpOperationRecord record, String code) {
        if (ToolOperationHandler.ExecutionKind.REMOTE_RECONCILABLE.name().equals(record.executionKind())
                && hasSendIntent(record)) {
            markUnknown(record, code);
        } else {
            markFailed(record, code);
        }
    }

    /** 结果未知时保留 UNKNOWN，不释放领域占用。 */
    private void markUnknown(McpOperationRecord record, String code) {
        boolean changed = store.deferRecovery(record.operationId(), owner, record.fence(),
                Instant.now().plusSeconds(Math.min(900, 30L << Math.min(5, record.attempt()))));
        if (!changed) {
            throw new ToolException("state_conflict", "操作终态已被其它 worker 接管");
        }
    }

    /**
     * 业务异常：确认凭证已在此线程消费，不能回 QUEUED 重放（会导致 confirmation_expired）。
     *
     * <p>可重试且未产生外部效果时转入 UNKNOWN，由恢复任务以新 fence 重新执行；
     * 其余直接终态失败。已存在发送意图的远端操作一律不重排。</p>
     */
    private void handleToolException(McpOperationRecord record, ToolException exception) {
        // 发送阶段可能在本次线程内推进，必须读取最新账本而非领取时的旧快照。
        boolean hasSendIntent = store.findById(record.operationId()).map(this::hasSendIntent).orElse(true);
        if (hasSendIntent && ToolOperationHandler.ExecutionKind.REMOTE_RECONCILABLE.name()
                .equals(record.executionKind())) {
            markUnknown(record, exception.code() == null ? "operation_outcome_unknown" : exception.code());
            return;
        }
        if (exception.retryable() && !hasSendIntent) {
            markUnknown(record, exception.code() == null ? "internal_error" : exception.code());
            return;
        }
        markFailed(record, exception.code() == null ? "internal_error" : exception.code());
    }

    /** publication 已进入发送阶段时，异常不得当作“未发送”重排。 */
    private boolean hasSendIntent(McpOperationRecord record) {
        return hasSendIntentStage(record.stage());
    }

    /** 排队已超截止且尚未发送时失败。 */
    private static boolean expired(McpOperationRecord record, Instant now) {
        return record.executionDeadlineAt() != null && record.executionDeadlineAt().isBefore(now)
                && !(ToolOperationHandler.ExecutionKind.REMOTE_RECONCILABLE.name().equals(record.executionKind())
                && hasSendIntentStage(record.stage()));
    }

    /** 发送意图及其后续阶段都不能按“未执行”过期。 */
    private static boolean hasSendIntentStage(String stage) {
        return stage != null && !"PREPARED".equals(stage) && !"FAILED".equals(stage);
    }

    /** 过期终态。 */
    private void expire(McpOperationRecord record) {
        markFailed(record, "operation_expired");
    }

    /** 心跳仅续租当前进程持有的 RUNNING 操作。 */
    private void heartbeatOnce() {
        if (closed.get()) {
            return;
        }
        try {
            store.heartbeatOwner(owner, Instant.now().plus(LEASE));
        } catch (RuntimeException exception) {
            log.warn("MCP 操作心跳未完成，下轮继续");
        }
    }

    /**
     * 清理已知终态的过期账本。
     *
     * <p>仍有非终态后续步骤的父操作不清理，否则步骤会失去授权与父操作引用。</p>
     *
     * @return 删除数量
     */
    public int cleanup() {
        return store.cleanupExpired(Instant.now(), 50, stepStore::hasUnfinished);
    }

    /** 业务权限桥接使用工具定义声明的访问要求；缺失时拒绝执行。 */
    private ToolAccess access(McpOperationRecord record, ToolOperationHandler handler) {
        ToolAccess access = operations.accesses().get(record.toolName() + "@" + record.toolVersion());
        if (access == null) {
            throw new ToolException("tool_unavailable", "工具权限定义缺失");
        }
        return access;
    }

    /** 关闭时停止新领取并有限等待。 */
    public void close() {
        closed.set(true);
        heartbeat.shutdownNow();
        localPool.shutdown();
        publicationPool.shutdown();
        mealPool.shutdown();
        try {
            localPool.awaitTermination(5, TimeUnit.SECONDS);
            publicationPool.awaitTermination(5, TimeUnit.SECONDS);
            mealPool.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    /** 固定容量、零等待队列。 */
    private static ThreadPoolExecutor pool(int size, String prefix) {
        return new ThreadPoolExecutor(size, size, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(size), task -> {
            Thread thread = new Thread(task, prefix);
            thread.setDaemon(true);
            return thread;
        }, new ThreadPoolExecutor.AbortPolicy());
    }

    /** 读取有界配置。 */
    private static int parameter(ConfigProvider config, String key, int fallback, int min, int max) {
        String value = config.getPlainValue(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed < min || parsed > max ? fallback : parsed;
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    /** 类型安全的 Map 转换。 */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> cast(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }
}
