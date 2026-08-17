package com.howe.ai.worker;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.howe.ai.domain.RunStatus;
import com.howe.ai.persistence.AiFactPersistenceService;
import com.howe.ai.queue.AiTaskMessage;
import com.howe.ai.runtime.ConfigAgentRuntime;
import com.howe.ai.runtime.RunExecutionContext;
import com.howe.ai.runtime.RuntimeResult;

/**
 * 运行任务的 Worker：先抢租约，再允许状态转移，随后驱动 Harness 执行并收敛到终态，
 * 成功后才由调用方 ACK。
 */
@Service
public class AiRunWorker {
    private final AiFactPersistenceService persistence;
    private final ConfigAgentRuntime runtime;
    private final Clock clock;

    @Autowired
    public AiRunWorker(AiFactPersistenceService persistence, ConfigAgentRuntime runtime) {
        this(persistence, runtime, Clock.systemUTC());
    }

    public AiRunWorker(AiFactPersistenceService persistence, ConfigAgentRuntime runtime, Clock clock) {
        this.persistence = Objects.requireNonNull(persistence, "AI 持久化服务未配置");
        this.runtime = Objects.requireNonNull(runtime, "AI 运行时未配置");
        this.clock = Objects.requireNonNull(clock, "时钟未配置");
    }

    public boolean handle(AiTaskMessage message, String workerId, Duration leaseDuration) {
        Objects.requireNonNull(message, "任务不能为空");
        long runId = parseRunId(message.runId());
        RunLease lease = new RunLease(message.runId(), workerId,
            Instant.now(clock).plus(requirePositive(leaseDuration)));
        Map<String, Object> run = persistence.getRun(runId);
        if (run == null || isTerminal(run.get("status")) || lease.isExpired(clock)) return false;

        int claimed = persistence.claimRunLease(runId, workerId, lease.leaseUntil(),
            String.valueOf(run.get("status")), message.idempotencyKey());
        if (claimed != 1) return false;
        String currentStatus = String.valueOf(run.get("status"));
        if ("run.cancel".equals(message.command())) {
            if (RunStatus.CANCEL_REQUESTED.name().equals(currentStatus)) return true;
            return persistence.transitionRunWithLease(runId, currentStatus,
                RunStatus.CANCEL_REQUESTED.name(), workerId) == 1;
        }
        // 已是 RUNNING 说明另一个 Worker 仍持有执行权，交由 Recovery Job 按租约判定。
        if (RunStatus.RUNNING.name().equals(currentStatus)) return true;
        if (persistence.transitionRunWithLease(runId, currentStatus, RunStatus.RUNNING.name(), workerId) != 1) {
            return false;
        }
        return execute(runId, workerId);
    }

    /** 驱动 Harness 并把结果落到终态；上下文非法时判为失败，不重投也不静默成功。 */
    private boolean execute(long runId, String workerId) {
        RuntimeResult result;
        try {
            RunExecutionContext context = RunExecutionContext.from(persistence.getRunExecutionContext(runId));
            result = runtime.run(String.valueOf(runId), context.request(), context.budget(),
                () -> isCancelRequested(runId));
        } catch (RuntimeException failure) {
            settle(runId, workerId, RunStatus.FAILED.name());
            return true;
        }
        settle(runId, workerId, terminalStatus(runId, result));
        return true;
    }

    private String terminalStatus(long runId, RuntimeResult result) {
        if (result.success()) return RunStatus.SUCCEEDED.name();
        return isCancelRequested(runId) ? RunStatus.CANCELLED.name() : RunStatus.FAILED.name();
    }

    /** 以执行期间的真实状态作为期望值，避免协作取消把条件更新打空。 */
    private void settle(long runId, String workerId, String target) {
        Map<String, Object> current = persistence.getRun(runId);
        if (current == null || isTerminal(current.get("status"))) return;
        persistence.transitionRunWithLease(runId, String.valueOf(current.get("status")), target, workerId);
    }

    private boolean isCancelRequested(long runId) {
        Map<String, Object> run = persistence.getRun(runId);
        return run != null && RunStatus.CANCEL_REQUESTED.name().equals(String.valueOf(run.get("status")));
    }

    public boolean heartbeat(String runId, String workerId, Duration extension) {
        return persistence.renewRunLease(parseRunId(runId), workerId,
            Instant.now(clock).plus(requirePositive(extension))) == 1;
    }

    public boolean canWrite(String runId, String workerId) {
        Map<String, Object> run = persistence.getRun(parseRunId(runId));
        if (run == null || isTerminal(run.get("status")) || !workerId.equals(String.valueOf(run.get("worker_id")))) {
            return false;
        }
        Object leaseUntil = run.get("lease_until");
        if (leaseUntil instanceof java.time.Instant instant) return instant.isAfter(Instant.now(clock));
        if (leaseUntil instanceof java.util.Date date) return date.toInstant().isAfter(Instant.now(clock));
        return false;
    }

    public long saveCheckpoint(String runId, long sequence, String stateJson) {
        return persistence.saveCheckpoint(parseRunId(runId), sequence, stateJson);
    }

    private static Duration requirePositive(Duration value) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException("租约时长必须为正数");
        }
        return value;
    }

    private static boolean isTerminal(Object status) {
        if (status == null) return false;
        try {
            return RunStatus.valueOf(String.valueOf(status)).isTerminal();
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static long parseRunId(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("运行标识必须为数字", exception);
        }
    }
}
