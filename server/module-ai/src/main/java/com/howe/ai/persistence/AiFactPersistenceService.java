package com.howe.ai.persistence;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Service
public class AiFactPersistenceService {
    private final AiFactMapper mapper;

    public AiFactPersistenceService(AiFactMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "AI 持久化 Mapper 未配置");
    }

    @Transactional
    public long createAgent(Map<String, Object> agent) {
        if (mapper.insertAgent(agent) != 1) throw new IllegalStateException("Agent 持久化失败");
        return ((Number) agent.get("agentId")).longValue();
    }

    public Map<String, Object> getAgent(long agentId) { return mapper.selectAgent(agentId); }

    public java.util.List<Map<String, Object>> listAgentVersions(long agentId, int offset, int limit) {
        return mapper.selectAgentVersions(agentId, Math.max(0, offset), Math.min(50, Math.max(1, limit)));
    }

    @Transactional
    public int updateDraft(long agentId, int expectedVersion, String draftJson, String updateBy) {
        return mapper.updateAgentDraft(agentId, expectedVersion, draftJson, updateBy);
    }


    @Transactional
    public int disableAgent(long agentId) { return mapper.disableAgent(agentId); }

    @Transactional
    public int publishAgent(long agentId, long versionId, String updateBy) {
        return mapper.publishAgent(agentId, versionId, updateBy);
    }

    public Map<String, Object> getConversationAgent(long conversationId, long userId) {
        return mapper.selectConversationAgent(conversationId, userId);
    }


    public long recordKey(long channelId, String encryptedSecret) {
        Map<String, Object> key = new HashMap<>();
        key.put("channelId", channelId);
        key.put("secret", encryptedSecret);
        mapper.insertApiKey(key);
        return ((Number) key.get("keyId")).longValue();
    }

    @Transactional
    public long insertMessage(long conversationId, Long runId, String role, String content, String metadataJson) {
        Map<String, Object> message = new HashMap<>();
        message.put("conversationId", conversationId);
        message.put("runId", runId);
        message.put("role", role);
        message.put("content", content);
        message.put("metadataJson", metadataJson == null ? "{}" : metadataJson);
        if (mapper.insertMessage(message) != 1) throw new IllegalStateException("消息持久化失败");
        return ((Number) message.get("messageId")).longValue();
    }

    public Map<String, Object> getRun(long runId) { return mapper.selectRunSnapshot(runId); }

    /** Harness 执行所需的上下文：固化快照 + 已发布版本 Prompt，不回读可变草稿。 */
    public Map<String, Object> getRunExecutionContext(long runId) {
        return mapper.selectRunExecutionContext(runId);
    }

    public void requireRunAccess(long runId, long userId) {
        if (userId <= 0 || mapper.countRunAccess(runId, userId) != 1) {
            throw new SecurityException("无权访问该运行");
        }
    }

    public java.util.List<Map<String, Object>> listEvents(long runId, long userId, int offset, int limit) {
        return mapper.selectRunEvents(runId, userId, Math.max(0, offset), Math.min(50, Math.max(1, limit)));
    }

    public java.util.List<Map<String, Object>> listEventsAfter(long runId, long sequence, int limit) {
        return mapper.selectRunEventsAfter(runId, Math.max(0, sequence), Math.min(50, Math.max(1, limit)));
    }
    public java.util.List<Map<String, Object>> listModelCalls(long runId, long userId, int offset, int limit) {
        return mapper.selectModelCalls(runId, userId, Math.max(0, offset), Math.min(50, Math.max(1, limit)));
    }

    public java.util.List<Map<String, Object>> listToolCalls(long runId, long userId, int offset, int limit) {
        return mapper.selectToolCalls(runId, userId, Math.max(0, offset), Math.min(50, Math.max(1, limit)));
    }

    @Transactional
    public long createRun(long conversationId, long agentId, long agentVersionId, String routeSnapshot,
                          String toolSnapshot, String budgetSnapshot, String idempotencyKey) {
        Long existing = mapper.selectRunIdByIdempotency(conversationId, idempotencyKey);
        if (existing != null) return existing;
        Map<String, Object> run = new HashMap<>();
        run.put("conversationId", conversationId); run.put("agentId", agentId); run.put("agentVersionId", agentVersionId);
        run.put("status", "QUEUED"); run.put("routeSnapshot", routeSnapshot); run.put("toolSnapshot", toolSnapshot);
        run.put("budgetSnapshot", budgetSnapshot); run.put("idempotencyKey", idempotencyKey);
        mapper.insertRun(run);
        long runId = ((Number) run.get("runId")).longValue();
        mapper.insertOutbox("run", runId, "run.execute", "{}", idempotencyKey);
        return runId;
    }

    @Transactional
    public int transitionRun(long runId, long userId, String expectedStatus, String status) {
        return mapper.updateRunStatus(runId, userId, expectedStatus, status);
    }

    @Transactional
    public int transitionRunWithLease(long runId, String expectedStatus, String status, String workerId) {
        return mapper.updateRunStatusWithLease(runId, expectedStatus, status, workerId);
    }

    public java.util.List<Map<String, Object>> listPendingOutbox(int limit) {
        return mapper.selectPendingOutbox(Math.min(100, Math.max(1, limit)));
    }

    @Transactional
    public int markOutboxSent(long outboxId) {
        return mapper.markOutboxSent(outboxId);
    }

    @Transactional
    public int recordOutboxFailure(long outboxId, String errorCode) {
        return mapper.recordOutboxFailure(outboxId, errorCode);
    }

    @Transactional
    public int claimRunLease(long runId, String workerId, java.time.Instant leaseUntil, String expectedStatus,
                             String idempotencyKey) {
        return mapper.claimRunLease(runId, workerId, leaseUntil, expectedStatus, idempotencyKey);
    }

    @Transactional
    public int renewRunLease(long runId, String workerId, java.time.Instant leaseUntil) {
        return mapper.renewRunLease(runId, workerId, leaseUntil);
    }

    public java.util.List<Map<String, Object>> listExpiredLeases(int maxRecoveryCount) {
        return mapper.selectExpiredLeases(Math.max(1, maxRecoveryCount));
    }

    @Transactional
    public int incrementRecoveryCount(long runId) {
        return mapper.incrementRecoveryCount(runId);
    }

    @Transactional
    public int markRunRecoveryDead(long runId, String errorCode) {
        return mapper.markRunRecoveryDead(runId, errorCode);
    }

    @Transactional
    public long saveCheckpoint(long runId, long sequenceNo, String stateJson) {
        mapper.insertCheckpoint(runId, sequenceNo, stateJson == null ? "{}" : stateJson);
        return sequenceNo;
    }

    @Transactional
    public long appendEvent(long runId, String eventType, String eventJson, String idempotencyKey) {
        mapper.lockRun(runId);
        // 唯一键 (run_id, idempotency_key) 决定了重复投递必须复用既有序号，
        // 否则直接 INSERT 会抛唯一键冲突并让整个 Run 失败。行锁保证检查与写入不会竞态。
        Long existing = mapper.selectEventSequenceByIdempotency(runId, idempotencyKey);
        if (existing != null) return existing;
        long sequence = mapper.selectNextEventSequence(runId);
        mapper.insertEvent(runId, sequence, eventType, eventJson, idempotencyKey);
        return sequence;
    }

    @Transactional
    public long publishAgentVersion(long agentId, int versionNo, String systemPrompt, String routeSnapshot,
                                    String toolSnapshot, String capabilitySnapshot, String budgetSnapshot) {
        Map<String, Object> version = new HashMap<>();
        version.put("agentId", agentId); version.put("versionNo", versionNo); version.put("systemPrompt", systemPrompt);
        version.put("routeSnapshot", routeSnapshot); version.put("toolSnapshot", toolSnapshot);
        version.put("capabilitySnapshot", capabilitySnapshot); version.put("budgetSnapshot", budgetSnapshot);
        mapper.insertAgentVersion(version);
        return ((Number) version.get("versionId")).longValue();
    }

    public Map<String, Object> getAgentVersion(long agentId, int versionNo) {
        return mapper.selectAgentVersion(agentId, versionNo);
    }

    /** 按 agentKey 查询可用于建会话的 Agent（校验由上层完成）。 */
    public Map<String, Object> findPublishableAgent(String agentKey) {
        return mapper.selectPublishableAgent(agentKey);
    }

    @Transactional
    public long createConversation(long agentId, long userId, String title) {
        Map<String, Object> conversation = new HashMap<>();
        conversation.put("agentId", agentId);
        conversation.put("userId", userId);
        conversation.put("title", title == null ? "" : title);
        if (mapper.insertConversation(conversation) != 1) {
            throw new IllegalStateException("会话创建失败");
        }
        return ((Number) conversation.get("conversationId")).longValue();
    }

    public java.util.List<Map<String, Object>> listConversations(long userId, String keyword, int pageNum, int pageSize) {
        int size = Math.max(1, Math.min(50, pageSize));
        return mapper.selectConversations(userId, keyword == null ? "" : keyword, Math.max(0, pageNum - 1) * size, size);
    }

    public int countConversations(long userId, String keyword) {
        return mapper.countConversations(userId, keyword == null ? "" : keyword);
    }
}
