package com.howe.ai.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.howe.ai.contract.AiRunEvent;
import com.howe.ai.persistence.AiFactPersistenceService;
import com.howe.ai.support.InMemoryFactMapper;

/**
 * 事件写入的幂等性与游标安全。
 *
 * <p>ai_run_event 上有唯一键 (run_id, idempotency_key)：消息重投时如果直接 INSERT
 * 会抛唯一键冲突并让整个 Run 失败；而 Last-Event-ID 只按冒号后的数字解析，
 * 客户端带来其它 Run 的事件 ID 时会把本 Run 的事件整段跳过。</p>
 */
class EventCursorIdempotencyTest {
    @Test
    void appendingSameIdempotencyKeyTwiceKeepsOneEventAndSameSequence() {
        InMemoryFactMapper facts = InMemoryFactMapper.create();
        facts.seedRun(1L, "RUNNING", "{}", "{}");
        AiFactPersistenceService persistence = new AiFactPersistenceService(facts.asMapper());

        long first = persistence.appendEvent(1L, "run.started", "{}", "1:1");
        long second = persistence.appendEvent(1L, "run.started", "{}", "1:1");

        assertEquals(first, second, "重复投递必须返回同一序号，而不是再分配一个");
        assertEquals(List.of("run.started"), facts.eventTypes(), "事件不得重复落库");
    }

    @Test
    void lastEventIdFromAnotherRunDoesNotSkipEvents() {
        InMemoryFactMapper facts = InMemoryFactMapper.create();
        facts.seedRun(7L, "SUCCEEDED", "{}", "{}");
        facts.seedEvent(7L, "run.started", "{}");
        facts.seedEvent(7L, "run.completed", "{}");
        AiFactEventStore store = new AiFactEventStore(new AiFactPersistenceService(facts.asMapper()));

        List<AiRunEvent> events = store.readAfter("7", "999:5");

        assertEquals(2, events.size(), "跨 Run 的游标必须被忽略，否则会整段跳过事件：" + events);
        assertTrue(events.stream().anyMatch(event -> event.type().equals("run.started")));
    }

    @Test
    void lastEventIdOfSameRunStillAdvancesCursor() {
        InMemoryFactMapper facts = InMemoryFactMapper.create();
        facts.seedRun(8L, "SUCCEEDED", "{}", "{}");
        facts.seedEvent(8L, "run.started", "{}");
        facts.seedEvent(8L, "run.completed", "{}");
        AiFactEventStore store = new AiFactEventStore(new AiFactPersistenceService(facts.asMapper()));

        List<AiRunEvent> events = store.readAfter("8", "8:1");

        assertEquals(1, events.size(), "同一 Run 的游标必须正常推进：" + events);
        assertEquals("run.completed", events.get(0).type());
    }
}
