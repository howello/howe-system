package com.howe.ai.application;

import com.howe.ai.domain.RunStatus;
import com.howe.ai.persistence.AiFactPersistenceService;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;

@Service
public class AiAdminApplicationService implements AdminAssistantApplicationService {
    private final AiFactPersistenceService persistence;

    public AiAdminApplicationService(AiFactPersistenceService persistence) {
        this.persistence = java.util.Objects.requireNonNull(persistence, "AI 持久化服务未配置");
    }

    @Override
    public void validateDraft(AgentDraft draft) {
        if (draft == null || draft.agentKey() == null || !draft.agentKey().matches("[a-z0-9][a-z0-9_-]{0,63}")
                || blank(draft.name()) || draft.name().length() > 128 || blank(draft.systemPrompt())) {
            throw new IllegalArgumentException("Agent 草稿字段不合法");
        }
        if (length(draft.systemPrompt()) > 20000 || length(draft.routeJson()) > 10000
                || length(draft.toolJson()) > 10000 || length(draft.budgetJson()) > 10000) {
            throw new IllegalArgumentException("Agent 草稿字段超长");
        }
    }

    @Override
    public String maskSecret(String secret) {
        if (secret == null || secret.isBlank()) return "";
        if (secret.length() <= 4) return "****";
        return "******" + secret.substring(secret.length() - 4);
    }

    public String validateConfigKey(String key) {
        if (key == null || !key.matches("[a-z0-9][a-z0-9_-]{0,63}")) {
            throw new IllegalArgumentException("配置编码不合法");
        }
        return key;
    }

    public int clampPageSize(int pageSize) {
        return Math.max(1, Math.min(MAX_PAGE_SIZE, pageSize));
    }

    public long createAgent(Map<String, Object> agent) {
        if (persistence == null) throw new IllegalStateException("AI 持久化服务未配置");
        return persistence.createAgent(agent);
    }

    public Map<String, Object> getAgent(long agentId) { return persistence.getAgent(agentId); }

    public List<Map<String, Object>> listAgentVersions(long agentId, int pageNum, int pageSize) {
        int size = clampPageSize(pageSize);
        return persistence.listAgentVersions(agentId, Math.max(0, pageNum - 1) * size, size);
    }

    public Map<String, Object> getAgentVersion(long agentId, int versionNo) {
        return persistence.getAgentVersion(agentId, versionNo);
    }

    public int updateAgent(long agentId, int expectedVersion, Map<String, Object> agent) {
        if (persistence == null) throw new IllegalStateException("AI 持久化服务未配置");
        String draft = String.valueOf(agent.getOrDefault("draftJson", "{}"));
        int changed = persistence.updateDraft(agentId, expectedVersion, draft, String.valueOf(agent.getOrDefault("updateBy", "admin")));
        if (changed != 1) throw new IllegalStateException("Agent 草稿已过期或已发布");
        return changed;
    }

    /**
     * 发布 Agent：按规格「发布时创建不可变版本」，从当前草稿生成包含 Prompt、路由、Tool 授权与预算
     * 快照的版本后再发布。
     *
     * <p>此前该方法要求版本必须已存在，但没有任何入口能创建版本（{@code publishAgentVersion} 在
     * 主代码里无调用方），导致草稿创建后永远无法发布，Chat/Run/SSE 整条链路不可达。
     * 现在版本不存在时按草稿快照补建，已存在则沿用既有版本（保持幂等，重复发布不会产生新快照）。</p>
     */
    public int publishAgent(long agentId, int versionNo, String updateBy) {
        if (persistence == null) throw new IllegalStateException("AI 持久化服务未配置");
        Map<String, Object> version = persistence.getAgentVersion(agentId, versionNo);
        if (version == null) {
            version = snapshotDraftAsVersion(agentId, versionNo);
        }
        return persistence.publishAgent(agentId, ((Number) version.get("version_id")).longValue(), updateBy);
    }

    /** 把当前草稿固化为指定版本号的不可变版本，并回读该版本行。 */
    private Map<String, Object> snapshotDraftAsVersion(long agentId, int versionNo) {
        Map<String, Object> agent = persistence.getAgent(agentId);
        if (agent == null) throw new IllegalArgumentException("Agent 不存在");
        AgentDraft draft = parseDraft(agent);
        // 快照前重新校验：发布是不可逆动作，不能把非法草稿固化成不可变版本
        validateDraft(draft);
        persistence.publishAgentVersion(agentId, versionNo, draft.systemPrompt(),
            snapshotOrEmpty(draft.routeJson(), "{}"), snapshotOrEmpty(draft.toolJson(), "[]"),
            "{}", snapshotOrEmpty(draft.budgetJson(), "{}"));
        Map<String, Object> created = persistence.getAgentVersion(agentId, versionNo);
        if (created == null) throw new IllegalStateException("Agent 版本快照创建失败");
        return created;
    }

    private AgentDraft parseDraft(Map<String, Object> agent) {
        Object raw = agent.get("draft_json");
        if (raw == null || String.valueOf(raw).isBlank()) throw new IllegalArgumentException("Agent 草稿为空，无法发布");
        com.alibaba.fastjson2.JSONObject draft;
        try {
            draft = com.alibaba.fastjson2.JSON.parseObject(String.valueOf(raw));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Agent 草稿不是合法 JSON，无法发布");
        }
        if (draft == null) throw new IllegalArgumentException("Agent 草稿不是合法 JSON，无法发布");
        return new AgentDraft(draft.getString("agentKey"), draft.getString("name"), draft.getString("systemPrompt"),
            draft.getString("routeJson"), draft.getString("toolJson"), draft.getString("budgetJson"));
    }

    /** 版本表的快照列是 JSON 类型，草稿里缺省时回落到合法空结构，避免写入 NULL 或非 JSON 文本。 */
    private static String snapshotOrEmpty(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    public int disableAgent(long agentId) {
        if (persistence == null) throw new IllegalStateException("AI 持久化服务未配置");
        return persistence.disableAgent(agentId);
    }

    public long recordKey(long channelId, String secret) {
        if (blank(secret)) throw new IllegalArgumentException("API Key 不能为空");
        if (persistence == null) throw new IllegalStateException("AI 持久化服务未配置");
        return persistence.recordKey(channelId, secret);
    }

    public long enqueueMessage(long conversationId, long userId, String content, String idempotencyKey) {
        if (blank(content) || blank(idempotencyKey)) throw new IllegalArgumentException("消息参数不合法");
        if (persistence == null) throw new IllegalStateException("AI 持久化服务未配置");
        Map<String, Object> agent = persistence.getConversationAgent(conversationId, userId);
        if (agent == null || !"0".equals(String.valueOf(agent.get("status"))) || agent.get("published_version_id") == null) {
            throw new IllegalStateException("会话 Agent 不可用");
        }
        long agentId = ((Number) agent.get("agent_id")).longValue();
        long versionId = ((Number) agent.get("published_version_id")).longValue();
        long runId = persistence.createRun(conversationId, agentId, versionId,
                String.valueOf(agent.getOrDefault("route_snapshot", "{}")), String.valueOf(agent.getOrDefault("tool_snapshot", "{}")),
                String.valueOf(agent.getOrDefault("budget_snapshot", "{}")), idempotencyKey);
        persistence.insertMessage(conversationId, runId, "user", content, "{}");
        return runId;
    }

    public Map<String, Object> getRun(long runId) {
        if (persistence == null) throw new IllegalStateException("AI 持久化服务未配置");
        return persistence.getRun(runId);
    }

    public Map<String, Object> getRun(long runId, long userId) {
        if (persistence == null) throw new IllegalStateException("AI 持久化服务未配置");
        persistence.requireRunAccess(runId, userId);
        return persistence.getRun(runId);
    }

    public int requestCancel(long runId, long userId) {
        if (persistence == null) return 0;
        persistence.requireRunAccess(runId, userId);
        int changed = persistence.transitionRun(runId, userId, RunStatus.QUEUED.name(), RunStatus.CANCEL_REQUESTED.name());
        if (changed == 0) changed = persistence.transitionRun(runId, userId, RunStatus.RUNNING.name(), RunStatus.CANCEL_REQUESTED.name());
        return changed;
    }

    public List<Map<String, Object>> listEvents(long runId, long userId, int pageNum, int pageSize) {
        if (persistence == null) throw new IllegalStateException("AI 持久化服务未配置");
        persistence.requireRunAccess(runId, userId);
        int safePage = Math.max(1, pageNum);
        return persistence.listEvents(runId, userId, (safePage - 1) * clampPageSize(pageSize), clampPageSize(pageSize));
    }

    public List<Map<String, Object>> listUsage(long runId, long userId, int pageNum, int pageSize) {
        if (persistence == null) throw new IllegalStateException("AI 持久化服务未配置");
        persistence.requireRunAccess(runId, userId);
        int size = clampPageSize(pageSize);
        return persistence.listModelCalls(runId, userId, Math.max(0, pageNum - 1) * size, size);
    }

    public List<Map<String, Object>> listToolUsage(long runId, long userId, int pageNum, int pageSize) {
        if (persistence == null) throw new IllegalStateException("AI 持久化服务未配置");
        persistence.requireRunAccess(runId, userId);
        int size = clampPageSize(pageSize);
        return persistence.listToolCalls(runId, userId, Math.max(0, pageNum - 1) * size, size);
    }

    /**
     * 创建会话：Chat 页发起对话的前置入口。会话归属当前登录用户，绑定一个已发布且未停用的 Agent。
     *
     * <p>与 {@link #enqueueMessage} 的守护一致——只有已发布（published_version_id 非空）且启用
     * （status='0'）的 Agent 才能建会话，否则会话建了也发不出消息。</p>
     */
    public long createConversation(String agentKey, long userId, String title) {
        if (blank(agentKey)) throw new IllegalArgumentException("Agent 编码不能为空");
        if (persistence == null) throw new IllegalStateException("AI 持久化服务未配置");
        Map<String, Object> agent = persistence.findPublishableAgent(agentKey);
        if (agent == null) throw new IllegalArgumentException("Agent 不存在: " + agentKey);
        if (!"0".equals(String.valueOf(agent.get("status")))) {
            throw new IllegalStateException("Agent 已停用，无法创建会话");
        }
        if (agent.get("published_version_id") == null) {
            throw new IllegalStateException("Agent 尚未发布，无法创建会话");
        }
        long agentId = ((Number) agent.get("agent_id")).longValue();
        return persistence.createConversation(agentId, userId, title);
    }

    public List<Map<String, Object>> listConversations(long userId, String keyword, int pageNum, int pageSize) {
        if (persistence == null) throw new IllegalStateException("AI 持久化服务未配置");
        return persistence.listConversations(userId, keyword, pageNum, pageSize);
    }

    public int countConversations(long userId, String keyword) {
        if (persistence == null) throw new IllegalStateException("AI 持久化服务未配置");
        return persistence.countConversations(userId, keyword);
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static int length(String value) { return value == null ? 0 : value.length(); }
}
