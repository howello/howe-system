package com.howe.ai.queue;

import java.util.Objects;

/** Redis Stream 中传递的最小运行命令。 */
public record AiTaskMessage(String command, String runId, String idempotencyKey, String payload) {
    public AiTaskMessage {
        requireText(command, "命令");
        requireText(runId, "运行标识");
        requireText(idempotencyKey, "幂等键");
        payload = Objects.requireNonNullElse(payload, "{}");
    }

    public static AiTaskMessage execute(String runId, String idempotencyKey, String payload) {
        return new AiTaskMessage("run.execute", runId, idempotencyKey, payload);
    }

    public static AiTaskMessage resume(String runId, String idempotencyKey, String payload) {
        return new AiTaskMessage("run.resume", runId, idempotencyKey, payload);
    }

    public static AiTaskMessage cancel(String runId, String idempotencyKey, String payload) {
        return new AiTaskMessage("run.cancel", runId, idempotencyKey, payload);
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + "不能为空");
    }
}
