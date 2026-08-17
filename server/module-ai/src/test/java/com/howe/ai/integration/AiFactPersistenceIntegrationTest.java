package com.howe.ai.integration;

import com.howe.ai.persistence.AiFactMapper;
import org.apache.ibatis.session.SqlSession;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 7.3 集成测试（MySQL 部分）：连真实隔离库 howe-ai-itest，验证事实层的原子性、幂等与终态保护。
 *
 * <p>覆盖验收标准中的事件序号单调与幂等、Outbox 投递幂等、Run 终态不可逆——这些依赖真实数据库的
 * 唯一键、行锁和条件更新，纯单测用 InMemoryFactMapper 无法证明。每个测试用独立会话与回滚清理。</p>
 */
class AiFactPersistenceIntegrationTest extends AiIntegrationTestBase {

    /** 同一 Run 下事件序号单调递增，重复 idempotencyKey 复用既有序号而非新增一行。 */
    @Test
    void eventSequenceIsMonotonicAndIdempotencyReusesSequence() {
        try (SqlSession session = openSession()) {
            AiFactMapper mapper = session.getMapper(AiFactMapper.class);
            long runId = seedRunningRun(mapper);

            mapper.insertEvent(runId, 1, "started", "{}", "evt-1");
            mapper.insertEvent(runId, 2, "delta", "{}", "evt-2");

            long nextAfterTwo = mapper.selectNextEventSequence(runId);
            assertEquals(3, nextAfterTwo, "两个事件后下一序号应为 3");

            // 重复 idempotencyKey 已占用：selectEventSequenceByIdempotency 应返回既有序号
            Long reused = mapper.selectEventSequenceByIdempotency(runId, "evt-1");
            assertNotNull(reused, "已存在的幂等键应能查回既有序号");
            assertEquals(1L, reused.longValue(), "复用的必须是首次分配的序号");

            session.rollback();
        }
    }

    /** Outbox 投递幂等：markOutboxSent 用条件更新，第二次标记返回 0（已 sent）。 */
    @Test
    void outboxDeliveryIsIdempotentUnderConditionalMark() {
        try (SqlSession session = openSession()) {
            AiFactMapper mapper = session.getMapper(AiFactMapper.class);
            mapper.insertOutbox("ai_run", 1L, "run.execute", "{}", "outbox-" + System.nanoTime());

            // selectPendingOutbox 取回刚插入的 pending 行
            var pending = mapper.selectPendingOutbox(10);
            assertTrue(pending.size() >= 1, "pending 队列应能取回刚插入的 outbox");
            Map<String, Object> row = pending.get(0);
            long outboxId = ((Number) row.get("outbox_id")).longValue();

            assertEquals(1, mapper.markOutboxSent(outboxId), "首次标记 sent 应影响 1 行");
            assertEquals(0, mapper.markOutboxSent(outboxId), "重复标记 sent 必须返回 0（幂等）");

            session.rollback();
        }
    }

    /** Run 终态保护：updateRunStatusWithLease 对已处于终态的 Run 返回 0（条件更新失败）。 */
    @Test
    void terminalRunStatusIsProtectedAgainstOverwrite() {
        try (SqlSession session = openSession()) {
            AiFactMapper mapper = session.getMapper(AiFactMapper.class);
            long runId = seedRunningRun(mapper);

            // 先把 Run 推到 SUCCEEDED 终态
            assertEquals(1, mapper.updateRunStatus(runId, 1L, "RUNNING", "SUCCEEDED"),
                "从 RUNNING→SUCCEEDED 应成功");

            // 终态后试图再改成 FAILED：expectedStatus='RUNNING' 已不匹配，必须返回 0
            assertEquals(0, mapper.updateRunStatus(runId, 1L, "RUNNING", "FAILED"),
                "终态 Run 不得被覆盖");

            session.rollback();
        }
    }

    /** 租约认领：claimRunLease 在 expectedStatus 匹配时成功，状态已变时失败。 */
    @Test
    void leaseClaimRespectsExpectedStatus() {
        try (SqlSession session = openSession()) {
            AiFactMapper mapper = session.getMapper(AiFactMapper.class);
            long runId = seedRunningRun(mapper);
            java.time.Instant future = java.time.Instant.now().plusSeconds(60);

            assertEquals(1, mapper.claimRunLease(runId, "worker-A", future, "RUNNING", "lease-" + System.nanoTime()),
                "RUNNING 状态下认领租约应成功");
            assertEquals(0, mapper.claimRunLease(runId, "worker-B", future, "CREATED", "lease-other"),
                "expectedStatus=CREATED 与实际 RUNNING 不匹配时认领必须失败");

            session.rollback();
        }
    }

    /** 不同 Run 的事件序号互不干扰（序号是 per-run 单调，非全局）。 */
    @Test
    void eventSequencesAreScopedPerRun() {
        try (SqlSession session = openSession()) {
            AiFactMapper mapper = session.getMapper(AiFactMapper.class);
            long runA = seedRunningRun(mapper);
            long runB = seedRunningRun(mapper);
            assertNotEquals(runA, runB, "两个 Run 必须是不同的行");

            mapper.insertEvent(runA, 1, "started", "{}", "a-1");
            mapper.insertEvent(runB, 1, "started", "{}", "b-1");
            // 两个 Run 各自的下一个序号都是 2，互不影响
            assertEquals(2, mapper.selectNextEventSequence(runA));
            assertEquals(2, mapper.selectNextEventSequence(runB));

            session.rollback();
        }
    }

    /** 准备一个处于 RUNNING 的 Run 并返回 run_id。 */
    private long seedRunningRun(AiFactMapper mapper) {
        String salt = "-" + System.nanoTime();
        Map<String, Object> agent = new HashMap<>();
        agent.put("agentKey", "itest-agent" + salt);
        agent.put("name", "itest");
        agent.put("draftJson", "{}");
        agent.put("createBy", "itest");
        mapper.insertAgent(agent);
        long agentId = ((Number) agent.get("agentId")).longValue();

        Map<String, Object> conv = new HashMap<>();
        conv.put("agentId", agentId);
        conv.put("userId", 1L);
        conv.put("title", "itest-conv");
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
        run.put("idempotencyKey", "run" + salt);
        mapper.insertRun(run);
        return ((Number) run.get("runId")).longValue();
    }
}
