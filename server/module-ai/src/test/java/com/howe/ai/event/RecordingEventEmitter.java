package com.howe.ai.event;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.howe.ai.contract.AiRunEvent;

/**
 * 测试用事件发射器：模拟 MySQL 事实源按 Run 分配单调序号的行为，
 * 让 Harness 测试观察到与生产一致的序号语义。
 */
public final class RecordingEventEmitter implements RunEventEmitter {
    private final List<AiRunEvent> events = new ArrayList<>();
    private long sequence;

    @Override
    public AiRunEvent publish(String runId, String type, String payload, String idempotencyKey) {
        AiRunEvent event = new AiRunEvent(runId, runId + ":" + (sequence + 1), ++sequence, type,
            Instant.EPOCH.plusSeconds(sequence), idempotencyKey, payload);
        events.add(event);
        return event;
    }

    public List<AiRunEvent> events() {
        return List.copyOf(events);
    }
}
