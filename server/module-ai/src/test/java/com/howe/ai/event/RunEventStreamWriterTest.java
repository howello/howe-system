package com.howe.ai.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringWriter;
import java.time.Clock;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.howe.ai.persistence.AiFactPersistenceService;
import com.howe.ai.support.InMemoryFactMapper;

/**
 * SSE 事件流契约。
 *
 * <p>此前 Controller 一次性返回 {@code List<ServerSentEvent>}：既不是长连接，Spring MVC 也没有
 * 对应的消息转换器，Last-Event-ID 补发逻辑因为没有实时通道而形同虚设。</p>
 */
class RunEventStreamWriterTest {
    private static final String BUDGET = "{\"maxDurationSeconds\":30}";

    @Test
    void replaysHistoryAfterLastEventIdAndStopsWhenRunIsTerminal() throws Exception {
        InMemoryFactMapper facts = InMemoryFactMapper.create();
        facts.seedRun(1L, "RUNNING", BUDGET, "{}");
        facts.seedEvent(1L, "run.started", "{}");
        facts.seedEvent(1L, "message.delta", "{\"content\":\"a\"}");
        facts.seedEvent(1L, "run.completed", "{}");
        facts.setStatus(1L, "SUCCEEDED");
        StringWriter out = new StringWriter();

        writer(facts).stream(1L, "1:1", out, () -> true);

        String payload = out.toString();
        assertFalse(payload.contains("run.started"), "Last-Event-ID 之前的事件不得重发");
        assertTrue(payload.contains("event: message.delta"), payload);
        assertTrue(payload.contains("event: run.completed"), payload);
        assertTrue(payload.contains("id: 1:3"), "必须携带事件 ID 供断线重连使用：" + payload);
    }

    @Test
    void doesNotResendEventsAlreadyDeliveredInThisConnection() throws Exception {
        InMemoryFactMapper facts = InMemoryFactMapper.create();
        facts.seedRun(2L, "RUNNING", BUDGET, "{}");
        facts.seedEvent(2L, "run.started", "{}");
        facts.setStatus(2L, "SUCCEEDED");
        StringWriter out = new StringWriter();

        writer(facts).stream(2L, null, out, () -> true);

        long occurrences = out.toString().lines().filter(line -> line.equals("event: run.started")).count();
        assertEquals(1, occurrences, "同一事件在一次连接内只能推送一次：" + out);
    }

    @Test
    void stopsImmediatelyWhenClientDisconnects() throws Exception {
        InMemoryFactMapper facts = InMemoryFactMapper.create();
        facts.seedRun(3L, "RUNNING", BUDGET, "{}");
        facts.seedEvent(3L, "run.started", "{}");
        StringWriter out = new StringWriter();

        // 运行未终结，只有断连才能让长连接退出，否则测试会一直阻塞。
        writer(facts).stream(3L, null, out, () -> false);

        assertFalse(out.toString().contains("event: run.started"), "断连后不应继续推送");
    }

    @Test
    void reportsDegradedStatusWhenRealtimeChannelIsUnavailable() throws Exception {
        InMemoryFactMapper facts = InMemoryFactMapper.create();
        facts.seedRun(4L, "SUCCEEDED", BUDGET, "{}");
        AiFactPersistenceService persistence = new AiFactPersistenceService(facts.asMapper());
        AiEventStore broken = (runId, cursor) -> { throw new IllegalStateException("redis down"); };
        AiRunEventService events = new AiRunEventService(new AiFactEventStore(persistence), broken);
        StringWriter out = new StringWriter();

        new RunEventStreamWriter(events, persistence, Duration.ZERO, Duration.ofSeconds(5), Clock.systemUTC())
            .stream(4L, null, out, () -> true);

        assertTrue(out.toString().contains("\"degraded\":true"), out.toString());
    }

    private static RunEventStreamWriter writer(InMemoryFactMapper facts) {
        AiFactPersistenceService persistence = new AiFactPersistenceService(facts.asMapper());
        AiEventStore realtime = (runId, cursor) -> List.of();
        AiRunEventService events = new AiRunEventService(new AiFactEventStore(persistence), realtime);
        return new RunEventStreamWriter(events, persistence, Duration.ZERO, Duration.ofSeconds(5), Clock.systemUTC());
    }
}
