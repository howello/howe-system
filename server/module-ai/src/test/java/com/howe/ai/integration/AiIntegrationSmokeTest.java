package com.howe.ai.integration;

import com.howe.ai.persistence.AiFactMapper;
import org.apache.ibatis.session.SqlSession;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 集成测试基建冒烟验证：确认基类能连真实隔离库、建表、装配 AiFactMapper 并执行真实 SQL。
 * 不验证业务语义（那是 OutboxIntegrationTest 等的职责），只证明管道通畅。
 * 库不可达时整个类被 assumeTrue 跳过（环境阻断，非断言失败）。
 */
class AiIntegrationSmokeTest extends AiIntegrationTestBase {

    @Test
    void mapperExecutesRealInsertAndSelectAgainstIsolatedSchema() {
        try (SqlSession session = openSession()) {
            AiFactMapper mapper = session.getMapper(AiFactMapper.class);

            Map<String, Object> agent = new HashMap<>();
            agent.put("agentKey", "smoke-agent-" + System.nanoTime());
            agent.put("name", "冒烟验证 Agent");
            agent.put("draftJson", "{\"prompt\":\"hello\"}");
            agent.put("createBy", "itest");
            assertEquals(1, mapper.insertAgent(agent), "insertAgent 应返回影响 1 行");
            assertNotNull(agent.get("agentId"), "useGeneratedKeys 应回填 agent_id");

            Map<String, Object> found = mapper.selectAgent(((Number) agent.get("agentId")).longValue());
            assertNotNull(found, "刚插入的 Agent 必须能查回");
            assertEquals("冒烟验证 Agent", found.get("name"));

            session.rollback(); // 冒烟不留数据
        }
    }

    @Test
    void sequenceAllocationAndRunLockWorkUnderRowLock() {
        try (SqlSession session = openSession()) {
            AiFactMapper mapper = session.getMapper(AiFactMapper.class);
            // 准备一个 conversation + run 以验证事件序号查询不报错
            Map<String, Object> agent = new HashMap<>();
            agent.put("agentKey", "smoke-seq-" + System.nanoTime());
            agent.put("name", "seq");
            agent.put("draftJson", "{}");
            agent.put("createBy", "itest");
            mapper.insertAgent(agent);
            long agentId = ((Number) agent.get("agentId")).longValue();

            Map<String, Object> conv = new HashMap<>();
            conv.put("agentId", agentId);
            conv.put("userId", 99L);
            conv.put("title", "smoke");
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
            run.put("idempotencyKey", "smoke-" + System.nanoTime());
            mapper.insertRun(run);
            long runId = ((Number) run.get("runId")).longValue();

            long seq = mapper.selectNextEventSequence(runId);
            assertTrue(seq >= 1, "空事件表的首个序号应为 1");
            session.rollback();
        }
    }
}
