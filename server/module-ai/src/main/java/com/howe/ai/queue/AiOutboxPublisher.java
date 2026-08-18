package com.howe.ai.queue;

import java.util.Map;
import java.util.Objects;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.howe.ai.persistence.AiFactPersistenceService;

/** 将 MySQL Outbox 可靠投递到 Redis Stream；状态更新只在投递成功后发生。 */
@Service
public class AiOutboxPublisher {
    private final AiFactPersistenceService persistence;
    private final RedisStreamTaskQueue queue;

    public AiOutboxPublisher(AiFactPersistenceService persistence, RedisStreamTaskQueue queue) {
        this.persistence = Objects.requireNonNull(persistence, "AI 持久化服务未配置");
        this.queue = Objects.requireNonNull(queue, "AI 任务队列未配置");
    }

    @Scheduled(fixedDelayString = "${ai.worker.outbox-delay-ms:1000}")
    public int publishPendingScheduled() {
        return publishPending(100);
    }

    public int publishPending(int limit) {
        int published = 0;
        for (Map<String, Object> event : persistence.listPendingOutbox(Math.max(1, Math.min(100, limit)))) {
            long outboxId = ((Number) event.get("outbox_id")).longValue();
            AiTaskMessage message = new AiTaskMessage(String.valueOf(event.get("event_type")),
                String.valueOf(event.get("aggregate_id")), String.valueOf(event.get("idempotency_key")),
                String.valueOf(event.getOrDefault("payload_json", "{}")));
            try {
                queue.enqueue(message);
                if (persistence.markOutboxSent(outboxId) == 1) published++;
            } catch (RuntimeException failure) {
                persistence.recordOutboxFailure(outboxId, failure.getClass().getSimpleName());
            }
        }
        return published;
    }
}
