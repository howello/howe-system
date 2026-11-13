package com.howe.mcp.operation;

import com.howe.common.utils.crypto.SecretCipher;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcOperations;

/**
 * 基于 MySQL 的后续步骤账本实现。
 *
 * <p>步骤与父操作同库同事务；加密载荷/回执只经 {@link SecretCipher} 存取，
 * 密钥不可用时直接失败，不降级明文。所有写回都带 owner/fence/lease 条件。</p>
 */
@Slf4j
public final class McpOperationStepStoreImpl implements McpOperationStepStore {

    /** 列投影，避免多处查询字段漂移。 */
    private static final String COLUMNS = """
            step_id,operation_id,step_key,handler_version,state,stage,result_code,encrypted_payload,
            encrypted_receipt,encrypted_result,lease_owner,lease_until,fence,attempt,next_attempt_at,
            execution_deadline_at,create_time,update_time
            """;

    private final JdbcOperations jdbc;
    private final SecretCipher cipher;

    /**
     * 注入同一数据源上的查询与主密钥。
     *
     * @param jdbc JDBC 操作
     * @param cipher 主密钥加解密
     */
    public McpOperationStepStoreImpl(JdbcOperations jdbc, SecretCipher cipher) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.cipher = Objects.requireNonNull(cipher);
    }

    /** 登记步骤；唯一键冲突视为已存在，不覆盖已有状态。 */
    @Override
    public boolean insert(String stepId, String operationId, String stepKey, String handlerVersion,
            String encryptedPayload, Instant executionDeadlineAt, Instant now) {
        try {
            return jdbc.update("""
                    insert into mcp_operation_step
                      (step_id,operation_id,step_key,handler_version,state,stage,encrypted_payload,
                       execution_deadline_at,create_time,update_time)
                    values (?,?,?,?,?,'QUEUED',?,?,current_timestamp(3),current_timestamp(3))
                    """, stepId, operationId, stepKey, handlerVersion, "QUEUED",
                    cipher.encrypt(encryptedPayload), timestamp(executionDeadlineAt)) == 1;
        } catch (DuplicateKeyException exception) {
            log.warn("MCP 后续步骤已存在，忽略重复登记：operationId={}，stepKey={}", operationId, stepKey);
            return false;
        }
    }

    /**
     * 领取一条可执行步骤：QUEUED 且已到下次尝试时间、UNKNOWN 且已到重试时间，
     * 或租约过期的 RUNNING。
     *
     * <p>UNKNOWN 必须能被重新领取，否则一次未确认的外部结果会永久滞留；
     * 但重新领取不等于自动重发，处理器必须自行判断阶段后再决定是否继续。</p>
     */
    @Override
    public Optional<McpOperationStepRecord> claimNext(String owner, Instant now, Instant leaseUntil) {
        List<String> candidates = jdbc.query("""
                select step_id from mcp_operation_step
                 where (state='QUEUED' or state='UNKNOWN'
                        or (state='RUNNING' and lease_until is not null and lease_until<?))
                   and not (state='UNKNOWN' and stage='SEND_INTENT')
                   and (next_attempt_at is null or next_attempt_at<=?)
                 order by coalesce(next_attempt_at,create_time),step_id limit 1
                """, (row, index) -> row.getString(1), timestamp(now), timestamp(now));
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        String stepId = candidates.get(0);
        int claimed = jdbc.update("""
                update mcp_operation_step
                   set state='RUNNING', lease_owner=?, lease_until=?, fence=fence+1, attempt=attempt+1,
                       update_time=current_timestamp(3)
                 where step_id=?
                   and (state='QUEUED' or state='UNKNOWN'
                        or (state='RUNNING' and lease_until is not null and lease_until<?))
                   and not (state='UNKNOWN' and stage='SEND_INTENT')
                   and (next_attempt_at is null or next_attempt_at<=?)
                """, owner, timestamp(leaseUntil), stepId, timestamp(now), timestamp(now));
        return claimed == 1 ? find(stepId) : Optional.empty();
    }

    /** 锁定有效步骤，锁持有到业务写入与步骤终态一起提交。 */
    @Override
    public boolean lockForExecution(String stepId, String owner, long fence) {
        return !jdbc.query("""
                select step_id from mcp_operation_step
                 where step_id=? and lease_owner=? and fence=? and state='RUNNING'
                   and lease_until>current_timestamp(3)
                   and (execution_deadline_at is null or execution_deadline_at>current_timestamp(3))
                 for update
                """, (row, index) -> row.getString(1), stepId, owner, fence).isEmpty();
    }

    /** 按父操作读取步骤，稳定按创建时间与主键排序。 */
    @Override
    public List<McpOperationStepRecord> findByOperation(String operationId) {
        return jdbc.query("select " + COLUMNS + """
                 from mcp_operation_step where operation_id=?
                 order by create_time,step_id
                """, McpOperationStepStoreImpl::map, operationId);
    }

    /** 在 owner/fence 有效时推进阶段，并保留租约。 */
    @Override
    public boolean updateStage(String stepId, String owner, long fence, String stage) {
        return jdbc.update("""
                update mcp_operation_step
                   set stage=?, update_time=current_timestamp(3)
                 where step_id=? and lease_owner=? and fence=? and state='RUNNING'
                   and lease_until>current_timestamp(3)
                """, stage, stepId, owner, fence) == 1;
    }

    /** 批量续租当前 worker 持有的 RUNNING 步骤。 */
    @Override
    public int heartbeatOwner(String owner, Instant leaseUntil) {
        return jdbc.update("""
                update mcp_operation_step
                   set lease_until=?, update_time=current_timestamp(3)
                 where lease_owner=? and state='RUNNING'
                   and lease_until>current_timestamp(3)
                   and (execution_deadline_at is null or execution_deadline_at>current_timestamp(3))
                """, timestamp(leaseUntil), owner);
    }

    /** 写入终态并清空租约。 */
    @Override
    public boolean finish(String stepId, String owner, long fence, String state, String stage, String resultCode,
            String encryptedResult) {
        return jdbc.update("""
                update mcp_operation_step
                   set state=?, stage=?, result_code=?, encrypted_result=?, lease_owner=null, lease_until=null,
                       update_time=current_timestamp(3)
                 where step_id=? and lease_owner=? and fence=? and state='RUNNING'
                   and lease_until>current_timestamp(3)
                """, state, stage, resultCode, cipher.encrypt(encryptedResult), stepId, owner, fence) == 1;
    }

    /** 释放租约并安排退避，保留非终态。 */
    @Override
    public boolean defer(String stepId, String owner, long fence, String state, String resultCode,
            Instant nextAttemptAt) {
        return jdbc.update("""
                update mcp_operation_step
                   set state=?, result_code=?, next_attempt_at=?, lease_owner=null, lease_until=null,
                       update_time=current_timestamp(3)
                 where step_id=? and lease_owner=? and fence=? and state='RUNNING'
                   and lease_until>current_timestamp(3)
                """, state, resultCode, timestamp(nextAttemptAt), stepId, owner, fence) == 1;
    }

    /** 读取并解密步骤载荷。 */
    @Override
    public String decryptedPayload(String stepId) {
        String encrypted = jdbc.query("select encrypted_payload from mcp_operation_step where step_id=?",
                (row, index) -> row.getString(1), stepId).stream().findFirst().orElse(null);
        return cipher.decrypt(encrypted);
    }

    /** 读取并解密步骤安全结果。 */
    @Override
    public String decryptedResult(String stepId) {
        String encrypted = jdbc.query("select encrypted_result from mcp_operation_step where step_id=?",
                (row, index) -> row.getString(1), stepId).stream().findFirst().orElse(null);
        return cipher.decrypt(encrypted);
    }

    /** 是否仍有非终态步骤。 */
    @Override
    public boolean hasUnfinished(String operationId) {
        Integer count = jdbc.queryForObject("""
                select count(*) from mcp_operation_step
                 where operation_id=? and state not in ('SUCCEEDED','FAILED','ABORTED','CANCELLED')
                """, Integer.class, operationId);
        return count != null && count > 0;
    }

    /** 按步骤标识读取完整记录。 */
    private Optional<McpOperationStepRecord> find(String stepId) {
        List<McpOperationStepRecord> rows = jdbc.query("select " + COLUMNS
                + " from mcp_operation_step where step_id=?", McpOperationStepStoreImpl::map, stepId);
        return rows.stream().findFirst();
    }

    /** 行映射。 */
    private static McpOperationStepRecord map(ResultSet row, int index) throws SQLException {
        return new McpOperationStepRecord(row.getString("step_id"), row.getString("operation_id"),
                row.getString("step_key"), row.getString("handler_version"), row.getString("state"),
                row.getString("stage"), row.getString("result_code"), row.getString("encrypted_payload"),
                row.getString("encrypted_receipt"), row.getString("encrypted_result"),
                row.getString("lease_owner"), instant(row.getTimestamp("lease_until")), row.getLong("fence"),
                row.getInt("attempt"), instant(row.getTimestamp("next_attempt_at")),
                instant(row.getTimestamp("execution_deadline_at")), instant(row.getTimestamp("create_time")),
                instant(row.getTimestamp("update_time")));
    }

    /** Timestamp 转 Instant。 */
    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    /** Instant 转 Timestamp。 */
    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }
}
