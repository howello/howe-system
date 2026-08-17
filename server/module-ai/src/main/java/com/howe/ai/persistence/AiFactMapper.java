package com.howe.ai.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface AiFactMapper {
    int updateAgentDraft(@Param("agentId") long agentId, @Param("expectedVersion") int expectedVersion,
                         @Param("draftJson") String draftJson, @Param("updateBy") String updateBy);
    int insertAgent(Map<String, Object> agent);
    Map<String, Object> selectAgent(@Param("agentId") long agentId);
    List<Map<String, Object>> selectAgentVersions(@Param("agentId") long agentId, @Param("offset") int offset, @Param("limit") int limit);
    int disableAgent(@Param("agentId") long agentId);
    int publishAgent(@Param("agentId") long agentId, @Param("versionId") long versionId, @Param("updateBy") String updateBy);
    Map<String, Object> selectConversationAgent(@Param("conversationId") long conversationId, @Param("userId") long userId);
    long insertApiKey(Map<String, Object> key);
    int insertRun(Map<String, Object> run);
    int insertMessage(Map<String, Object> message);
    Long selectRunIdByIdempotency(@Param("conversationId") long conversationId, @Param("idempotencyKey") String idempotencyKey);
    int updateRunStatus(@Param("runId") long runId, @Param("userId") long userId,
                        @Param("expectedStatus") String expectedStatus, @Param("status") String status);
    int updateRunStatusWithLease(@Param("runId") long runId, @Param("expectedStatus") String expectedStatus,
                                 @Param("status") String status, @Param("workerId") String workerId);
    long selectNextEventSequence(@Param("runId") long runId);
    Long selectEventSequenceByIdempotency(@Param("runId") long runId,
                                          @Param("idempotencyKey") String idempotencyKey);
    long lockRun(@Param("runId") long runId);
    int insertEvent(@Param("runId") long runId, @Param("sequenceNo") long sequenceNo,
                    @Param("eventType") String eventType, @Param("eventJson") String eventJson,
                    @Param("idempotencyKey") String idempotencyKey);
    Map<String, Object> selectRunSnapshot(@Param("runId") long runId);
    Map<String, Object> selectRunExecutionContext(@Param("runId") long runId);
    int countRunAccess(@Param("runId") long runId, @Param("userId") long userId);
    List<Map<String, Object>> selectRunEvents(@Param("runId") long runId, @Param("userId") long userId,
                                               @Param("offset") int offset, @Param("limit") int limit);
    int insertOutbox(@Param("aggregateType") String aggregateType, @Param("aggregateId") long aggregateId,
                     @Param("eventType") String eventType, @Param("payloadJson") String payloadJson,
                     @Param("idempotencyKey") String idempotencyKey);
    List<Map<String, Object>> selectPendingOutbox(@Param("limit") int limit);
    int markOutboxSent(@Param("outboxId") long outboxId);
    int recordOutboxFailure(@Param("outboxId") long outboxId, @Param("errorCode") String errorCode);
    int claimRunLease(@Param("runId") long runId, @Param("workerId") String workerId,
                      @Param("leaseUntil") java.time.Instant leaseUntil, @Param("expectedStatus") String expectedStatus,
                      @Param("idempotencyKey") String idempotencyKey);
    int renewRunLease(@Param("runId") long runId, @Param("workerId") String workerId,
                      @Param("leaseUntil") java.time.Instant leaseUntil);
    List<Map<String, Object>> selectExpiredLeases(@Param("maxRecoveryCount") int maxRecoveryCount);
    int incrementRecoveryCount(@Param("runId") long runId);
    int markRunRecoveryDead(@Param("runId") long runId, @Param("errorCode") String errorCode);
    int insertCheckpoint(@Param("runId") long runId, @Param("sequenceNo") long sequenceNo,
                         @Param("stateJson") String stateJson);
    int insertAgentVersion(Map<String, Object> version);
    Map<String, Object> selectAgentVersion(@Param("agentId") long agentId, @Param("versionNo") int versionNo);
    List<Map<String, Object>> selectModelCalls(@Param("runId") long runId, @Param("userId") long userId,
                                                @Param("offset") int offset, @Param("limit") int limit);
    List<Map<String, Object>> selectToolCalls(@Param("runId") long runId, @Param("userId") long userId,
                                               @Param("offset") int offset, @Param("limit") int limit);
    List<Map<String, Object>> selectRunEventsAfter(@Param("runId") long runId, @Param("sequence") long sequence,
                                                   @Param("limit") int limit);
    /** 按 agentKey 查询可用于建会话的 Agent（已发布且启用），供 createConversation 校验。 */
    Map<String, Object> selectPublishableAgent(@Param("agentKey") String agentKey);
    /** 插入会话，返回自增 conversation_id。 */
    int insertConversation(Map<String, Object> conversation);
    /** 按用户分页查询会话（仅本人或管理员），按更新时间倒序。 */
    List<Map<String, Object>> selectConversations(@Param("userId") long userId, @Param("keyword") String keyword,
                                                  @Param("offset") int offset, @Param("limit") int limit);
    /** 按用户统计会话总数（仅本人或管理员）。 */
    int countConversations(@Param("userId") long userId, @Param("keyword") String keyword);
}