package com.howe.ai.event;

import java.io.IOException;
import java.io.Writer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;

import com.howe.ai.contract.AiRunEvent;
import com.howe.ai.domain.RunStatus;
import com.howe.ai.persistence.AiFactPersistenceService;

/**
 * SSE 事件流写入器：负责补发、去重、游标推进和终态收敛。
 *
 * <p>与 Servlet 类型解耦，只面向 {@link Writer}，因此可以脱离容器验证长连接语义。
 * 事件先经 {@link AiRunEventService} 合并事实源与实时通道；Redis 不可用时降级为纯 MySQL 读取，
 * 并通过 status 事件如实告知客户端，而不是静默丢事件。</p>
 */
public class RunEventStreamWriter {
    private final AiRunEventService events;
    private final AiFactPersistenceService persistence;
    private final Duration pollInterval;
    private final Duration maxDuration;
    private final Clock clock;

    public RunEventStreamWriter(AiRunEventService events, AiFactPersistenceService persistence,
                                Duration pollInterval, Duration maxDuration, Clock clock) {
        this.events = Objects.requireNonNull(events, "事件服务未配置");
        this.persistence = Objects.requireNonNull(persistence, "AI 持久化服务未配置");
        this.pollInterval = pollInterval == null ? Duration.ofMillis(500) : pollInterval;
        this.maxDuration = maxDuration == null ? Duration.ofMinutes(10) : maxDuration;
        this.clock = Objects.requireNonNull(clock, "时钟未配置");
    }

    /**
     * 保持长连接推送事件，直到 Run 终结、客户端断开或达到最大连接时长。
     *
     * @param lastEventId 客户端重连时携带的 Last-Event-ID，之前的事件不再重发
     * @param connected   连接是否仍然可用，断开后立即停止推送
     */
    public void stream(long runId, String lastEventId, Writer out, BooleanSupplier connected) throws IOException {
        Instant deadline = Instant.now(clock).plus(maxDuration);
        Set<String> delivered = new HashSet<>();
        String cursor = lastEventId;
        Boolean lastDegraded = null;

        while (connected.getAsBoolean()) {
            EventReadResult result = events.readAfterWithStatus(String.valueOf(runId), cursor);
            if (lastDegraded == null || lastDegraded != result.degraded()) {
                writeStatus(out, result.degraded());
                lastDegraded = result.degraded();
            }
            boolean advanced = false;
            for (AiRunEvent event : result.events()) {
                if (!delivered.add(event.eventId())) continue;
                writeEvent(out, event);
                cursor = event.eventId();
                advanced = true;
            }
            out.flush();
            // 终态之后仍要把剩余事件排空，因此只有本轮无新事件时才结束。
            if (!advanced && isTerminal(runId)) return;
            if (!Instant.now(clock).isBefore(deadline)) {
                writeComment(out, "stream-timeout");
                return;
            }
            if (!advanced) writeComment(out, "keep-alive");
            if (!sleep()) return;
        }
    }

    private void writeStatus(Writer out, boolean degraded) throws IOException {
        out.write("event: status\n");
        out.write("data: {\"degraded\":" + degraded + "}\n\n");
    }

    private void writeEvent(Writer out, AiRunEvent event) throws IOException {
        out.write("id: " + event.eventId() + "\n");
        out.write("event: " + event.type() + "\n");
        // SSE 要求每一行都带 data 前缀，payload 含换行时必须逐行拆分。
        for (String line : String.valueOf(event.payload()).split("\n", -1)) {
            out.write("data: " + line + "\n");
        }
        out.write("\n");
    }

    private void writeComment(Writer out, String comment) throws IOException {
        out.write(": " + comment + "\n\n");
        out.flush();
    }

    private boolean isTerminal(long runId) {
        Map<String, Object> run = persistence.getRun(runId);
        if (run == null) return true;
        try {
            return RunStatus.valueOf(String.valueOf(run.get("status"))).isTerminal();
        } catch (IllegalArgumentException unknown) {
            return false;
        }
    }

    private boolean sleep() {
        if (pollInterval.isZero() || pollInterval.isNegative()) return true;
        try {
            Thread.sleep(pollInterval.toMillis());
            return true;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
