package com.howe.mcp.operation;

import com.howe.common.utils.crypto.SecretCipher;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 基于 MySQL 的操作账本实现。
 *
 * <p>状态推进全部使用显式条件做 CAS；围栏令牌随领取递增，旧 worker 即使迟到也无法
 * 覆盖新状态。加密请求/结果只经 {@link SecretCipher} 存取，密钥不可用时直接拒绝，
 * 不降级明文。</p>
 */
@Slf4j
public final class McpOperationStoreImpl implements McpOperationStore {

    /** 账本列投影，避免多处查询字段漂移。 */
    private static final String COLUMNS = """
            operation_id,user_id,registered_client_id,grant_id,grant_version,resource_uri,approved_scopes,
            prepared_dept_id,account_context_hash,tool_name,tool_version,execution_kind,queue_group,
            idempotency_hash,arguments_hash,configuration_hash,target_type,target_id,expected_version,
            state,stage,confirmed_by,confirmed_at,confirmation_expires_at,prepare_expires_at,execution_deadline_at,
            next_attempt_at,lease_owner,lease_until,fence,attempt,result_code,result_bytes,row_version,
            payload_retain_until,retain_until,finished_at,create_time,update_time
            """;

    /** 记录大小上限，超出参数错误。 */
    private final JdbcOperations jdbc;
    private final TransactionTemplate transactions;
    private final SecretCipher cipher;

    /**
     * 注入同一数据源上的查询与事务。
     *
     * @param jdbc JDBC 操作
     * @param transactions 事务模板
     * @param cipher 主密钥加解密
     */
    public McpOperationStoreImpl(JdbcOperations jdbc, TransactionTemplate transactions, SecretCipher cipher) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.transactions = Objects.requireNonNull(transactions);
        this.cipher = Objects.requireNonNull(cipher);
    }

    /** 按幂等唯一键查询。 */
    @Override
    public Optional<McpOperationRecord> findByIdempotency(Long userId, String registeredClientId, String toolName,
            String toolVersion, byte[] idempotencyHash) {
        List<McpOperationRecord> rows = jdbc.query("select " + COLUMNS + """
                 from mcp_operation
                 where user_id=? and registered_client_id=? and tool_name=? and tool_version=? and idempotency_hash=?
                """, McpOperationStoreImpl::map, userId, registeredClientId, toolName, toolVersion, idempotencyHash);
        return rows.stream().findFirst();
    }

    /** 按操作标识查询本人操作。 */
    @Override
    public Optional<McpOperationRecord> findOwned(String operationId, Long userId) {
        return find("select " + COLUMNS + " from mcp_operation where operation_id=? and user_id=?",
                operationId, userId);
    }

    /** 按操作标识查询，不附加账号条件；仅供平台内部恢复与步骤执行。 */
    @Override
    public Optional<McpOperationRecord> findById(String operationId) {
        return find("select " + COLUMNS + " from mcp_operation where operation_id=?", operationId);
    }

    /** 按操作、账号、原授权与内部客户端联合读取。 */
    @Override
    public Optional<McpOperationRecord> findBound(String operationId, Long userId, Long grantId,
            String registeredClientId) {
        return find("select " + COLUMNS + """
                 from mcp_operation
                 where operation_id=? and user_id=? and grant_id=? and registered_client_id=?
                """, operationId, userId, grantId, registeredClientId);
    }

    /** 分页查询本人操作，固定按创建时间倒序与主键兜底。 */
    @Override
    public List<McpOperationRecord> pageOwned(Long userId, String state, long offset, int limit) {
        if (state == null || state.isBlank()) {
            return jdbc.query("select " + COLUMNS + """
                     from mcp_operation where user_id=?
                     order by create_time desc,operation_id desc limit ? offset ?
                    """, McpOperationStoreImpl::map, userId, limit, offset);
        }
        return jdbc.query("select " + COLUMNS + """
                 from mcp_operation where user_id=? and state=?
                 order by create_time desc,operation_id desc limit ? offset ?
                """, McpOperationStoreImpl::map, userId, state, limit, offset);
    }

    /** 统计本人操作。 */
    @Override
    public long countOwned(Long userId, String state) {
        Long total = state == null || state.isBlank()
                ? jdbc.queryForObject("select count(*) from mcp_operation where user_id=?", Long.class, userId)
                : jdbc.queryForObject("select count(*) from mcp_operation where user_id=? and state=?", Long.class,
                        userId, state);
        return total == null ? 0 : total;
    }

    /** 插入准备态；唯一键冲突不静默覆盖。 */
    @Override
    public void insertPrepared(McpOperationRecord record, String encryptedRequest) {
        Objects.requireNonNull(record);
        String cipherText = cipher.encrypt(encryptedRequest);
        try {
            jdbc.update("""
                    insert into mcp_operation (
                        operation_id,user_id,registered_client_id,grant_id,grant_version,resource_uri,approved_scopes,
                        prepared_dept_id,account_context_hash,tool_name,tool_version,execution_kind,queue_group,
                        idempotency_hash,arguments_hash,configuration_hash,target_type,target_id,expected_version,
                        encrypted_request,state,stage,prepare_expires_at,execution_deadline_at,next_attempt_at,
                        payload_retain_until,retain_until)
                    values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                    """, record.operationId(), record.userId(), record.registeredClientId(), record.grantId(),
                    record.grantVersion(), record.resourceUri(), record.approvedScopes(), record.preparedDeptId(),
                    record.accountContextHash(), record.toolName(), record.toolVersion(), record.executionKind(),
                    record.queueGroup(), record.idempotencyHash(), record.argumentsHash(), record.configurationHash(),
                    record.targetType(), record.targetId(), record.expectedVersion(), cipherText,
                    McpOperationState.PREPARED, "PREPARED", timestamp(record.prepareExpiresAt()),
                    timestamp(record.executionDeadlineAt()), timestamp(record.nextAttemptAt()),
                    timestamp(record.payloadRetainUntil()), timestamp(record.retainUntil()));
        } catch (DuplicateKeyException exception) {
            throw new IllegalStateException("操作幂等键冲突", exception);
        }
    }

    /** 写入确认上下文，固定摘要与会话绑定并递增版本。 */
    @Override
    public boolean openConfirmationContext(String operationId, Long userId, long expectedRowVersion, byte[] summaryHash,
            byte[] sessionHash, Instant confirmationExpiresAt) {
        return jdbc.update("""
                update mcp_operation
                   set confirmation_summary_hash=?, confirmation_session_hash=?, confirmation_expires_at=?,
                       row_version=row_version+1, update_time=current_timestamp(3)
                 where operation_id=? and user_id=? and state=? and row_version=?
                   and prepare_expires_at>current_timestamp(3)
                """, summaryHash, sessionHash, timestamp(confirmationExpiresAt), operationId, userId,
                McpOperationState.PREPARED, expectedRowVersion) == 1;
    }

    /** 确认：PREPARED → QUEUED，凭证哈希只在此处写入。 */
    @Override
    public boolean confirm(String operationId, Long userId, long expectedRowVersion, byte[] summaryHash,
            byte[] confirmationHash, byte[] sessionHash, Instant executionDeadlineAt, Instant now) {
        return jdbc.update("""
                update mcp_operation
                   set state=?, confirmed_by=?, confirmed_at=?, confirmation_hash=?, execution_deadline_at=?,
                       row_version=row_version+1, update_time=current_timestamp(3)
                 where operation_id=? and user_id=? and state=? and row_version=?
                   and prepare_expires_at>? and confirmation_summary_hash=? and confirmation_session_hash=?
                   and confirmation_used_at is null
                   and (confirmation_expires_at is null or confirmation_expires_at>?)
                """, McpOperationState.QUEUED, userId, timestamp(now), confirmationHash,
                timestamp(executionDeadlineAt), operationId, userId, McpOperationState.PREPARED, expectedRowVersion,
                timestamp(now), summaryHash, sessionHash, timestamp(now)) == 1;
    }

    /** 派发时消费确认凭证：领取已把状态改为 RUNNING，按 owner/fence/租约消费。 */
    @Override
    public boolean consumeConfirmation(String operationId, String owner, long fence, Instant now) {
        return jdbc.update("""
                update mcp_operation
                   set confirmation_hash=null, confirmation_used_at=coalesce(confirmation_used_at,?),
                       update_time=current_timestamp(3)
                 where operation_id=? and fence=? and state=? and lease_owner=?
                   and (confirmation_hash is not null or confirmation_used_at is not null)
                   and confirmed_by=user_id and confirmed_at is not null
                   and (execution_deadline_at>?
                        or (execution_kind='REMOTE_RECONCILABLE'
                            and stage in ('REMOTE_SEND_INTENT','REMOTE_COMMITTED_LOCAL_PENDING')))
                   and lease_until>current_timestamp(3)
                """, timestamp(now), operationId, fence, McpOperationState.RUNNING, owner, timestamp(now)) == 1;
    }

    /**
     * 领取下一条 QUEUED：先选候选，再用条件更新抢占并递增围栏。
     *
     * <p>短事务、不持锁跨外部调用；领取失败返回空，由调度下轮重试。</p>
     */
    @Override
    public Optional<McpOperationRecord> claimNextQueued(String owner, Instant now, Instant leaseUntil) {
        List<String> candidates = jdbc.query("""
                select operation_id from mcp_operation
                 where state=? and (lease_until is null or lease_until<?)
                   and (next_attempt_at is null or next_attempt_at<=?)
                 order by create_time,operation_id limit 1
                """, (row, index) -> row.getString(1), McpOperationState.QUEUED, timestamp(now), timestamp(now));
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        String operationId = candidates.get(0);
        int claimed = jdbc.update("""
                update mcp_operation
                   set state=?, lease_owner=?, lease_until=?, fence=fence+1, attempt=attempt+1,
                       update_time=current_timestamp(3)
                 where operation_id=? and state=? and (lease_until is null or lease_until<?)
                   and (next_attempt_at is null or next_attempt_at<=?)
                """, McpOperationState.RUNNING, owner, timestamp(leaseUntil), operationId,
                McpOperationState.QUEUED, timestamp(now), timestamp(now));
        return claimed == 1 ? findOwnedAny(operationId) : Optional.empty();
    }

    /** 领取到期的 UNKNOWN 或租约过期的 RUNNING 用于恢复。 */
    @Override
    public Optional<McpOperationRecord> claimNextRecoverable(String owner, Instant now, Instant leaseUntil) {
        // 本地写可重新执行；远端已发送的操作由执行器只读对账。
        List<String> candidates = jdbc.query("""
                select operation_id from mcp_operation
                 where (state=? or (state=? and lease_until is not null and lease_until<?))
                   and (next_attempt_at is null or next_attempt_at<=?)
                 order by coalesce(next_attempt_at,create_time),operation_id limit 1
                """, (row, index) -> row.getString(1), McpOperationState.UNKNOWN, McpOperationState.RUNNING,
                timestamp(now), timestamp(now));
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        String operationId = candidates.get(0);
        int claimed = jdbc.update("""
                update mcp_operation
                   set state=?, lease_owner=?, lease_until=?, fence=fence+1, attempt=attempt+1,
                       update_time=current_timestamp(3)
                 where operation_id=?
                   and (state=? or (state=? and lease_until<?))
                   and (next_attempt_at is null or next_attempt_at<=?)
                """, McpOperationState.RUNNING, owner, timestamp(leaseUntil), operationId,
                McpOperationState.UNKNOWN, McpOperationState.RUNNING, timestamp(now), timestamp(now));
        return claimed == 1 ? findOwnedAny(operationId) : Optional.empty();
    }

    /** 恢复未决：保留 UNKNOWN 并设置下次尝试时间。 */
    @Override
    public boolean deferRecovery(String operationId, String owner, long fence, Instant nextAttemptAt) {
        return jdbc.update("""
                update mcp_operation
                   set state=?, lease_owner=null, lease_until=null, next_attempt_at=?,
                       row_version=row_version+1, update_time=current_timestamp(3)
                 where operation_id=? and lease_owner=? and fence=? and state=?
                   and lease_until>current_timestamp(3)
                """, McpOperationState.UNKNOWN, timestamp(nextAttemptAt), operationId, owner, fence,
                McpOperationState.RUNNING) == 1;
    }

    /** 恢复确证成功，复用相同的持有者、围栏和租约校验。 */
    @Override
    public boolean recoverSucceeded(String operationId, String owner, long fence, String resultCode, String encryptedResult) {
        return transition(operationId, fence, McpOperationState.RUNNING, McpOperationState.SUCCEEDED,
                "LOCAL_COMMITTED", resultCode, encryptedResult, "RECONCILED", "recovery", owner);
    }

    /** 恢复确证未生效，拒绝过期持有者写入。 */
    @Override
    public boolean recoverFailed(String operationId, String owner, long fence, String resultCode) {
        return transition(operationId, fence, McpOperationState.RUNNING, McpOperationState.FAILED,
                "FAILED", resultCode, null, "RECONCILED", "recovery", owner);
    }

    /** 心跳续租；不递增行版本、不生成事件。 */
    @Override
    public boolean heartbeat(String operationId, String owner, long fence, Instant leaseUntil) {
        return jdbc.update("""
                update mcp_operation set lease_until=?, update_time=current_timestamp(3)
                 where operation_id=? and lease_owner=? and fence=? and state=?
                   and lease_until>current_timestamp(3)
                """, timestamp(leaseUntil), operationId, owner, fence, McpOperationState.RUNNING) == 1;
    }

    /**
     * 在有效租约下记录发送意图；owner/fence/lease/state 条件原子阻止旧 worker 发起新 PUT。
     */
    @Override
    public boolean checkpointExternalSendIntent(String operationId, String owner, long fence, String stage) {
        return jdbc.update("""
                update mcp_operation
                   set stage=?, row_version=row_version+1, update_time=current_timestamp(3)
                 where operation_id=? and lease_owner=? and fence=? and state=?
                   and lease_until>current_timestamp(3)
                """, stage, operationId, owner, fence, McpOperationState.RUNNING) == 1;
    }

    /** owner/fence/有效租约下推进发送阶段；不释放租约、不改变主状态。 */
    @Override
    public boolean updateStage(String operationId, String owner, long fence, String fromStage, String toStage) {
        return Boolean.TRUE.equals(transactions.execute(status -> {
            int changed = jdbc.update("""
                    update mcp_operation
                       set stage=?, row_version=row_version+1, update_time=current_timestamp(3)
                     where operation_id=? and lease_owner=? and fence=? and state=? and stage=?
                       and lease_until>current_timestamp(3)
                    """, toStage, operationId, owner, fence, McpOperationState.RUNNING, fromStage);
            if (changed != 1) {
                return false;
            }
            event(operationId, McpOperationState.RUNNING, McpOperationState.RUNNING, toStage, null,
                    "STAGE", "worker", fence);
            return true;
        }));
    }

    /** ledger REMOTE_COMMITTED 阶段更新，必须由 publication 回执同一外层事务调用。 */
    @Override
    public boolean markRemoteCommittedStage(String operationId, String owner, long fence) {
        return markRemoteCommittedStage(operationId, owner, fence, "REMOTE_SEND_INTENT");
    }

    /** ledger 回执阶段更新；REQUIRED 事务加入 publication service 的外层事务。 */
    @Override
    public boolean markRemoteCommittedStage(String operationId, String owner, long fence, String expectedStage) {
        return Boolean.TRUE.equals(transactions.execute(status -> {
            int changed = jdbc.update("""
                    update mcp_operation
                       set stage='REMOTE_COMMITTED_LOCAL_PENDING', row_version=row_version+1,
                           update_time=current_timestamp(3)
                     where operation_id=? and lease_owner=? and fence=? and state=?
                       and stage=? and lease_until>current_timestamp(3)
                    """, operationId, owner, fence, McpOperationState.RUNNING, expectedStage);
            if (changed != 1) {
                return false;
            }
            event(operationId, McpOperationState.RUNNING, McpOperationState.RUNNING,
                    "REMOTE_COMMITTED_LOCAL_PENDING", "remote_committed", "REMOTE_COMMITTED", "worker", fence);
            return true;
        }));
    }

    /** 对账成功时推进阶段；保持主状态 RUNNING，后续 complete 与 SUCCEEDED 同事务。 */
    @Override
    public boolean reconcileRemoteCommittedStage(String operationId, String owner, long fence) {
        return Boolean.TRUE.equals(transactions.execute(status -> {
            int changed = jdbc.update("""
                    update mcp_operation
                       set stage='REMOTE_COMMITTED_LOCAL_PENDING', row_version=row_version+1,
                           update_time=current_timestamp(3)
                     where operation_id=? and lease_owner=? and fence=? and state=?
                       and lease_until>current_timestamp(3)
                    """, operationId, owner, fence, McpOperationState.RUNNING);
            if (changed != 1) {
                return false;
            }
            event(operationId, McpOperationState.RUNNING, McpOperationState.RUNNING,
                    "REMOTE_COMMITTED_LOCAL_PENDING", "reconciled", "REMOTE_COMMITTED", "recovery", fence);
            return true;
        }));
    }

    /** 批量续租当前进程持有的 RUNNING 操作。 */
    @Override
    public int heartbeatOwner(String owner, Instant leaseUntil) {
        return jdbc.update("""
                update mcp_operation set lease_until=?, update_time=current_timestamp(3)
                 where lease_owner=? and state=? and lease_until>current_timestamp(3)
                """, timestamp(leaseUntil), owner, McpOperationState.RUNNING);
    }

    /** 围栏匹配时推进状态与阶段，并写一条版本事件。 */
    @Override
    public boolean transition(String operationId, long fence, String from, String to, String stage, String resultCode,
            String encryptedResult, String eventType, String requestId, String owner) {
        String cipherText = encryptedResult == null ? null : cipher.encrypt(encryptedResult);
        return Boolean.TRUE.equals(transactions.execute(status -> {
            int changed = jdbc.update("""
                    update mcp_operation
                       set state=?, stage=?, result_code=?, encrypted_result=coalesce(?,encrypted_result),
                           lease_owner=null, lease_until=null, row_version=row_version+1,
                           finished_at=case when ? in ('SUCCEEDED','FAILED','CANCELLED','EXPIRED')
                                            then current_timestamp(3) else finished_at end,
                           update_time=current_timestamp(3)
                     where operation_id=? and fence=? and state=? and lease_owner=?
                       and lease_until>current_timestamp(3)
                    """, to, stage, resultCode, cipherText, to, operationId, fence, from, owner);
            if (changed != 1) {
                return false;
            }
            event(operationId, from, to, stage, resultCode, eventType, requestId, fence);
            return true;
        }));
    }

    /** 只对确定尚未产生外部效果的失败回到队列。 */
    @Override
    public boolean releaseToQueued(String operationId, String owner, long fence, Instant nextAttemptAt) {
        return jdbc.update("""
                update mcp_operation
                   set state=?, lease_owner=null, lease_until=null, next_attempt_at=?,
                       row_version=row_version+1, update_time=current_timestamp(3)
                 where operation_id=? and fence=? and state=? and lease_owner=?
                   and lease_until>current_timestamp(3)
                """, McpOperationState.QUEUED, timestamp(nextAttemptAt), operationId, fence,
                McpOperationState.RUNNING, owner) == 1;
    }

    /** 锁定账本行，业务写及终态提交期间禁止其他恢复进程接管。 */
    @Override
    public boolean lockForExecution(String operationId, String owner, long fence) {
        return !jdbc.query("""
                select operation_id from mcp_operation
                 where operation_id=? and lease_owner=? and fence=? and state=?
                   and lease_until>current_timestamp(3) for update
                """, (row, index) -> row.getString(1), operationId, owner, fence,
                McpOperationState.RUNNING).isEmpty();
    }

    /** 取消准备态不依赖执行租约，仍需本人、状态及行版本同时匹配。 */
    @Override
    public boolean cancelPrepared(String operationId, Long userId, long rowVersion) {
        return Boolean.TRUE.equals(transactions.execute(status -> {
            int changed = jdbc.update("""
                    update mcp_operation set state='CANCELLED', result_code='rejected',
                           row_version=row_version+1, finished_at=current_timestamp(3),
                           update_time=current_timestamp(3)
                     where operation_id=? and user_id=? and row_version=? and state='PREPARED'
                       and lease_owner is null
                    """, operationId, userId, rowVersion);
            if (changed != 1) {
                return false;
            }
            event(operationId, McpOperationState.PREPARED, McpOperationState.CANCELLED, "PREPARED",
                    "rejected", "REJECTED", "browser", 0);
            return true;
        }));
    }

    /** 读取并解密规范化请求。 */
    @Override
    public String decryptedRequest(String operationId) {
        List<String> rows = jdbc.query("select encrypted_request from mcp_operation where operation_id=?",
                (row, index) -> row.getString(1), operationId);
        return rows.isEmpty() || rows.get(0) == null ? null : cipher.decrypt(rows.get(0));
    }

    /** 读取并解密安全结果。 */
    @Override
    public String decryptedResult(String operationId) {
        List<String> rows = jdbc.query("select encrypted_result from mcp_operation where operation_id=?",
                (row, index) -> row.getString(1), operationId);
        return rows.isEmpty() || rows.get(0) == null ? null : cipher.decrypt(rows.get(0));
    }

    /** 清理已知终态过期账本；UNKNOWN/RUNNING/QUEUED/PREPARED 不按保留期删除。 */
    @Override
    public int cleanupExpired(Instant now, int limit) {
        return cleanupExpired(now, limit, operationId -> false);
    }

    /**
     * 清理已过保留期的已知终态账本；仍有非终态后续步骤的父操作跳过。
     */
    @Override
    public int cleanupExpired(Instant now, int limit, java.util.function.Predicate<String> hasUnfinishedSteps) {
        List<String> expired = jdbc.query("""
                select operation_id from mcp_operation
                 where retain_until<? and state in (?,?,?)
                 order by retain_until,operation_id limit ?
                """, (row, index) -> row.getString(1), timestamp(now), McpOperationState.SUCCEEDED,
                McpOperationState.FAILED, McpOperationState.CANCELLED, limit);
        int removed = 0;
        for (String operationId : expired) {
            if (hasUnfinishedSteps != null && hasUnfinishedSteps.test(operationId)) {
                continue;
            }
            Integer deleted = transactions.execute(status -> {
                jdbc.update("delete from mcp_operation_event where operation_id=?", operationId);
                return jdbc.update("""
                        delete from mcp_operation where operation_id=? and retain_until<?
                          and state in (?,?,?)
                        """, operationId, timestamp(now), McpOperationState.SUCCEEDED, McpOperationState.FAILED,
                        McpOperationState.CANCELLED);
            });
            removed += deleted == null ? 0 : deleted;
        }
        return removed;
    }

    /** 写状态事件；事件类型与阶段必须由代码白名单提供。 */
    private void event(String operationId, String from, String to, String stage, String resultCode, String eventType,
            String requestId, long fence) {
        Long version = jdbc.queryForObject("select row_version from mcp_operation where operation_id=?",
                Long.class, operationId);
        jdbc.update("""
                insert into mcp_operation_event (operation_id,operation_version,event_type,from_state,to_state,
                    stage,result_code,request_id,fence)
                values (?,?,?,?,?,?,?,?,?)
                """, operationId, version == null ? 0 : version, eventType, from, to, stage, resultCode,
                requestId == null ? "internal" : requestId, fence);
    }

    /** 按主键读取，不限制账号；仅平台内部派发使用。 */
    private Optional<McpOperationRecord> findOwnedAny(String operationId) {
        return find("select " + COLUMNS + " from mcp_operation where operation_id=?", operationId);
    }

    /** 通用单行读取。 */
    private Optional<McpOperationRecord> find(String sql, Object... arguments) {
        List<McpOperationRecord> rows = jdbc.query(sql, McpOperationStoreImpl::map, arguments);
        return rows.stream().findFirst();
    }

    /** 生成操作标识：32 位小写十六进制。 */
    public static String newOperationId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /** 计算 SHA-256。 */
    public static byte[] sha256(String text) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前运行环境缺少 SHA-256", exception);
        }
    }

    /** 行投影。 */
    private static McpOperationRecord map(ResultSet row, int index) throws SQLException {
        return new McpOperationRecord(row.getString("operation_id"), row.getLong("user_id"),
                row.getString("registered_client_id"), row.getLong("grant_id"), row.getLong("grant_version"),
                row.getString("resource_uri"), row.getString("approved_scopes"),
                row.getObject("prepared_dept_id", Long.class), row.getBytes("account_context_hash"),
                row.getString("tool_name"), row.getString("tool_version"), row.getString("execution_kind"),
                row.getString("queue_group"), row.getBytes("idempotency_hash"), row.getBytes("arguments_hash"),
                row.getBytes("configuration_hash"), row.getString("target_type"), row.getString("target_id"),
                row.getString("expected_version"), row.getString("state"), row.getString("stage"),
                row.getObject("confirmed_by", Long.class), instant(row.getTimestamp("confirmed_at")),
                instant(row.getTimestamp("confirmation_expires_at")),
                instant(row.getTimestamp("prepare_expires_at")), instant(row.getTimestamp("execution_deadline_at")),
                instant(row.getTimestamp("next_attempt_at")), row.getString("lease_owner"),
                instant(row.getTimestamp("lease_until")), row.getLong("fence"), row.getInt("attempt"),
                row.getString("result_code"), row.getObject("result_bytes", Integer.class), row.getLong("row_version"),
                instant(row.getTimestamp("payload_retain_until")), instant(row.getTimestamp("retain_until")),
                instant(row.getTimestamp("finished_at")), instant(row.getTimestamp("create_time")),
                instant(row.getTimestamp("update_time")));
    }

    /** 空时间保持为空。 */
    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    /** 可空时间转 SQL 时间戳。 */
    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }
}
