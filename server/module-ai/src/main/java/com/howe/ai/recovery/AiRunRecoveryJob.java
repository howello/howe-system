package com.howe.ai.recovery;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.howe.ai.persistence.AiFactPersistenceService;
import com.howe.ai.queue.AiTaskMessage;
import com.howe.ai.queue.RedisStreamTaskQueue;

/** 处理过期租约和超过恢复上限的逻辑死信。 */
@Service
public class AiRunRecoveryJob {
    private final AiFactPersistenceService persistence;
    private final RedisStreamTaskQueue queue;
    private final int maxRecoveryCount;

    @Autowired
    public AiRunRecoveryJob(AiFactPersistenceService persistence, RedisStreamTaskQueue queue) {
        this(persistence, queue, 3);
    }

    public AiRunRecoveryJob(AiFactPersistenceService persistence, RedisStreamTaskQueue queue, int maxRecoveryCount) {
        this.persistence = Objects.requireNonNull(persistence, "AI 持久化服务未配置");
        this.queue = Objects.requireNonNull(queue, "AI 任务队列未配置");
        if (maxRecoveryCount < 1) throw new IllegalArgumentException("最大恢复次数必须为正数");
        this.maxRecoveryCount = maxRecoveryCount;
    }

    @Scheduled(fixedDelayString = "${ai.worker.recovery-delay-ms:30000}")
    public int recoverExpired() {
        int recovered = 0;
        List<Map<String, Object>> runs = persistence.listExpiredLeases(maxRecoveryCount);
        for (Map<String, Object> run : runs) {
            long runId = ((Number) run.get("run_id")).longValue();
            int attempts = ((Number) run.getOrDefault("recovery_count", 0)).intValue();
            String idempotencyKey = "recovery:" + runId + ":" + (attempts + 1);
            if (attempts >= maxRecoveryCount) {
                AiTaskMessage dead = AiTaskMessage.resume(String.valueOf(runId), idempotencyKey, "{}");
                queue.deadLetter(dead, "max-recovery-count");
                persistence.markRunRecoveryDead(runId, "MAX_RECOVERY");
            } else {
                persistence.incrementRecoveryCount(runId);
                queue.enqueue(AiTaskMessage.resume(String.valueOf(runId), idempotencyKey, "{}"));
            }
            recovered++;
        }
        return recovered;
    }

    public int maxRecoveryCount() {
        return maxRecoveryCount;
    }
}
