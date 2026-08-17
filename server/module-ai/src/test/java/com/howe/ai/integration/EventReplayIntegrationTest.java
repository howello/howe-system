package com.howe.ai.integration;

import com.howe.ai.contract.AiRunEvent;
import com.howe.ai.event.AiFactEventStore;
import com.howe.ai.persistence.AiFactMapper;
import org.apache.ibatis.session.SqlSession;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 7.3 集成测试（事件补发）：连真实隔离库，验证事实源的事件游标补发与 Last-Event-ID 语义。
 *
 * <p>覆盖验收标准「SSE 客户端使用 Last-Event-ID 时只补发之后的事件、不重不丢」——
 * AiFactEventStore.parseSequence 与 readAfter 是补发的核心，依赖真实事件行的 sequence_no 单调。</p>
 */
class EventReplayIntegrationTest extends AiIntegrationTestBase {

    @Test
    void readAfterCursorReplaysOnlyLaterEvents() {
        try (SqlSession session = openSession()) {
            AiFactMapper mapper = session.getMapper(AiFactMapper.class);
            AiFactEventStore store = new AiFactEventStore(wrap(mapper));
            long runId = seedRunningRun(mapper);

            // 写三条事件：seq 1/2/3
            mapper.insertEvent(runId, 1, "started", "{}", "e1");
            mapper.insertEvent(runId, 2, "delta", "{}", "e2");
            mapper.insertEvent(runId, 3, "delta", "{}", "e3");

            String runIdStr = String.valueOf(runId);
            // 游标指向 seq 1（runId:1），应只补发 seq 2、3
            List<AiRunEvent> replayed = store.readAfter(runIdStr, runIdStr + ":1");
            assertEquals(2, replayed.size(), "游标指向 seq1 时只补发后续 2 条");
            assertEquals(2L, replayed.get(0).sequence());
            assertEquals(3L, replayed.get(1).sequence());

            session.rollback();
        }
    }

    @Test
    void lastEventIdFromDifferentRunIsTreatedAsNoCursor() {
        try (SqlSession session = openSession()) {
            AiFactMapper mapper = session.getMapper(AiFactMapper.class);
            AiFactEventStore store = new AiFactEventStore(wrap(mapper));
            long runId = seedRunningRun(mapper);
            mapper.insertEvent(runId, 1, "started", "{}", "x1");

            // 携带其它 Run 的游标：parseSequence 视为无游标，从头补发
            List<AiRunEvent> replayed = store.readAfter(String.valueOf(runId), "999:5");
            assertEquals(1, replayed.size(), "跨 Run 游标按无游标处理，从头补发 1 条");

            session.rollback();
        }
    }

    @Test
    void blankCursorReplaysFromBeginning() {
        try (SqlSession session = openSession()) {
            AiFactMapper mapper = session.getMapper(AiFactMapper.class);
            AiFactEventStore store = new AiFactEventStore(wrap(mapper));
            long runId = seedRunningRun(mapper);
            mapper.insertEvent(runId, 1, "started", "{}", "b1");
            mapper.insertEvent(runId, 2, "delta", "{}", "b2");

            List<AiRunEvent> replayed = store.readAfter(String.valueOf(runId), null);
            assertEquals(2, replayed.size(), "空游标从头补发全部事件");

            session.rollback();
        }
    }

    /** AiFactEventStore 通过 AiFactPersistenceService 调用 mapper，这里用一个轻量适配把真实 mapper 包成 service。 */
    private static com.howe.ai.persistence.AiFactPersistenceService wrap(AiFactMapper mapper) {
        return new com.howe.ai.persistence.AiFactPersistenceService(mapper);
    }

    private long seedRunningRun(AiFactMapper mapper) {
        String salt = "-" + System.nanoTime();
        Map<String, Object> agent = new HashMap<>();
        agent.put("agentKey", "evt-agent" + salt);
        agent.put("name", "evt");
        agent.put("draftJson", "{}");
        agent.put("createBy", "itest");
        mapper.insertAgent(agent);
        long agentId = ((Number) agent.get("agentId")).longValue();

        Map<String, Object> conv = new HashMap<>();
        conv.put("agentId", agentId);
        conv.put("userId", 1L);
        conv.put("title", "evt-conv");
        mapper.insertConversation(conv);
        long convId = ((Number) conv.get("conversationId")).longValue();

        Map<String, Object> run = new HashMap<>();
        run.put("conversationId", convId);
        run.put("agentId", agentId);
        run.put("agentVersionId", 0L);
        run.put("status", "RUNNING");
        run.put("routeSnapshot", "{}");
        run.put("toolSnapshot", "{}");
        run.put("budgetSnapshot", "{}");
        run.put("idempotencyKey", "evt-run" + salt);
        mapper.insertRun(run);
        return ((Number) run.get("runId")).longValue();
    }
}
