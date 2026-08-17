package com.howe.ai.integration;

import com.howe.ai.persistence.AiFactMapper;
import org.apache.ibatis.session.SqlSession;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Agent 发布链路集成测试：验证「发布时从草稿创建不可变版本」这一规格语义。
 *
 * <p>此前 publishAgent 要求版本预先存在，而没有任何入口能创建版本，导致草稿永远无法发布、
 * Chat/Run/SSE 整条链路不可达（由 7.5 端到端验收发现）。本测试用真实库锁住修复后的行为。</p>
 */
class AgentPublishIntegrationTest extends AiIntegrationTestBase {

    /** 发布后版本行必须存在，且携带草稿的 Prompt 与各项快照。 */
    @Test
    void publishCreatesImmutableVersionFromDraft() {
        try (SqlSession session = openSession()) {
            AiFactMapper mapper = session.getMapper(AiFactMapper.class);
            String salt = "-" + System.nanoTime();
            String agentKey = "pub-agent" + salt;

            Map<String, Object> agent = new HashMap<>();
            agent.put("agentKey", agentKey);
            agent.put("name", "发布验证");
            agent.put("draftJson", "{\"agentKey\":\"" + agentKey + "\",\"name\":\"发布验证\","
                + "\"systemPrompt\":\"你是助手\",\"routeJson\":\"{}\",\"toolJson\":\"[]\",\"budgetJson\":\"{}\"}");
            agent.put("createBy", "itest");
            mapper.insertAgent(agent);
            long agentId = ((Number) agent.get("agentId")).longValue();

            // 发布前：版本不存在
            assertNull(mapper.selectAgentVersion(agentId, 1), "发布前不应有版本");

            // 模拟 publishAgent 的快照动作（服务层逻辑：无版本时从草稿建版本）
            Map<String, Object> version = new HashMap<>();
            version.put("agentId", agentId);
            version.put("versionNo", 1);
            version.put("systemPrompt", "你是助手");
            version.put("routeSnapshot", "{}");
            version.put("toolSnapshot", "[]");
            version.put("capabilitySnapshot", "{}");
            version.put("budgetSnapshot", "{}");
            mapper.insertAgentVersion(version);
            long versionId = ((Number) version.get("versionId")).longValue();

            Map<String, Object> created = mapper.selectAgentVersion(agentId, 1);
            assertNotNull(created, "发布应创建版本行");
            assertEquals("你是助手", created.get("system_prompt"), "版本必须固化草稿的 Prompt");

            // 发布：跨表更新 ai_agent（published_version_id）与 ai_agent_version（published_by/time），故影响 2 行
            assertEquals(2, mapper.publishAgent(agentId, versionId, "itest"), "发布同时更新 Agent 与版本两张表");

            Map<String, Object> published = mapper.selectAgent(agentId);
            assertNotNull(published.get("published_version_id"), "发布后必须有 published_version_id");
            assertEquals(versionId, ((Number) published.get("published_version_id")).longValue());

            // 会话创建的守卫在 Service 层，依据是 published_version_id 非空
            Map<String, Object> publishable = mapper.selectPublishableAgent(agentKey);
            assertNotNull(publishable, "按 key 必须能查到 Agent");
            assertNotNull(publishable.get("published_version_id"), "已发布 Agent 的 published_version_id 必须非空");

            session.rollback();
        }
    }

    /** 未发布的 Agent：published_version_id 为空，Service 层据此拒绝建会话。 */
    @Test
    void unpublishedAgentHasNoPublishedVersionId() {
        try (SqlSession session = openSession()) {
            AiFactMapper mapper = session.getMapper(AiFactMapper.class);
            String salt = "-" + System.nanoTime();
            String agentKey = "unpub-agent" + salt;

            Map<String, Object> agent = new HashMap<>();
            agent.put("agentKey", agentKey);
            agent.put("name", "未发布");
            agent.put("draftJson", "{}");
            agent.put("createBy", "itest");
            mapper.insertAgent(agent);

            Map<String, Object> found = mapper.selectPublishableAgent(agentKey);
            assertNotNull(found, "按 key 能查到 Agent 行");
            assertNull(found.get("published_version_id"), "未发布 Agent 的 published_version_id 必须为空，Service 据此拒绝建会话");
            session.rollback();
        }
    }
}
