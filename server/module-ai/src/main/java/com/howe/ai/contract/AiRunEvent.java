package com.howe.ai.contract;

import java.time.Instant;

public record AiRunEvent(String runId, String eventId, long sequence, String type,
                         Instant occurredAt, String idempotencyKey, String payload) {
    public AiRunEvent {
        if (sequence <= 0) throw new IllegalArgumentException("事件序号必须为正数");
        requireText(runId, "运行标识");
        requireText(eventId, "事件标识");
        requireText(type, "事件类型");
        if (occurredAt == null) throw new NullPointerException("事件时间不能为空");
        requireText(idempotencyKey, "幂等键");
        requireText(payload, "事件载荷");
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + "不能为空");
    }
}
