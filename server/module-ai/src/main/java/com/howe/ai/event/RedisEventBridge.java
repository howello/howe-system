package com.howe.ai.event;

import java.util.Objects;

import org.springframework.stereotype.Service;

import com.howe.ai.contract.AiRunEvent;

/** 实时事件桥：只有事实源 append 成功后才向 Redis 发布，保证 MySQL 始终是唯一事实源。 */
@Service
public class RedisEventBridge implements RunEventEmitter {
    private final AiFactEventStore facts;
    private final RealtimeEventPublisher realtime;

    public RedisEventBridge(AiFactEventStore facts, RealtimeEventPublisher realtime) {
        this.facts = Objects.requireNonNull(facts, "事实事件源不能为空");
        this.realtime = Objects.requireNonNull(realtime, "实时事件发布器不能为空");
    }

    @Override
    public AiRunEvent publish(String runId, String type, String payload, String idempotencyKey) {
        AiRunEvent event = facts.append(runId, type, payload, idempotencyKey);
        try {
            realtime.publish(event);
        } catch (RuntimeException unavailable) {
            // 实时通道属于短期加速路径；发布失败不影响事实源，订阅方会从 MySQL 补发。
            return event;
        }
        return event;
    }
}
