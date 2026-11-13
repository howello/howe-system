package com.howe.mcp.operation;

import java.time.Instant;
import java.util.Optional;

/**
 * 操作账本存储接口。
 *
 * <p>所有写入都以显式状态条件、行版本或围栏作为 CAS 前提；返回 {@code false}
 * 表示状态已被其它事务推进，调用方必须重新读取后决定，不能盲目重试业务效果。</p>
 *
 * <p>本接口不暴露明文请求/结果；加密载荷由实现内部调用 {@code SecretCipher} 处理。</p>
 */
public interface McpOperationStore {

    /**
     * 按幂等唯一键查询。
     *
     * @param userId 账号
     * @param registeredClientId 内部客户端登记主键
     * @param toolName 工具名
     * @param toolVersion 工具版本
     * @param idempotencyHash 幂等键哈希
     * @return 已存在的操作
     */
    Optional<McpOperationRecord> findByIdempotency(Long userId, String registeredClientId, String toolName,
            String toolVersion, byte[] idempotencyHash);

    /**
     * 按操作标识查询本人操作。
     *
     * @param operationId 操作标识
     * @param userId 账号
     * @return 操作记录
     */
    Optional<McpOperationRecord> findOwned(String operationId, Long userId);

    /**
     * 按操作标识读取记录，不附加账号条件。
     *
     * <p>只供平台内部恢复与步骤执行使用；调用方必须先通过父操作绑定复核授权，
     * 不得把结果直接投影给用户。</p>
     *
     * @param operationId 操作标识
     * @return 账本记录
     */
    Optional<McpOperationRecord> findById(String operationId);

    /**
     * 按内部绑定读取操作，用于确认入口复核原 grant 与客户端。
     *
     * @param operationId 操作标识
     * @param userId 账号
     * @param grantId 原授权
     * @param registeredClientId 内部客户端登记主键
     * @return 操作记录
     */
    Optional<McpOperationRecord> findBound(String operationId, Long userId, Long grantId, String registeredClientId);

    /**
     * 分页查询本人操作。
     *
     * @param userId 账号
     * @param state 可选状态过滤
     * @param offset 偏移
     * @param limit 条数
     * @return 操作列表
     */
    java.util.List<McpOperationRecord> pageOwned(Long userId, String state, long offset, int limit);

    /**
     * 统计本人操作。
     *
     * @param userId 账号
     * @param state 可选状态过滤
     * @return 总数
     */
    long countOwned(Long userId, String state);

    /**
     * 插入准备态操作；唯一键冲突时抛出受控冲突，由调用方复核是否同摘要。
     *
     * @param record 待插入记录
     * @param encryptedRequest 加密后的规范化请求
     */
    void insertPrepared(McpOperationRecord record, String encryptedRequest);

    /**
     * 写入确认上下文：固定确认摘要哈希、会话哈希与确认期限，并递增行版本。
     *
     * @param operationId 操作标识
     * @param userId 账号
     * @param expectedRowVersion 期望行版本
     * @param summaryHash 确认摘要哈希
     * @param sessionHash 浏览器会话绑定哈希
     * @param confirmationExpiresAt 确认到期时间
     * @return 成功时返回 true
     */
    boolean openConfirmationContext(String operationId, Long userId, long expectedRowVersion, byte[] summaryHash,
            byte[] sessionHash, Instant confirmationExpiresAt);

    /**
     * 浏览器确认：PREPARED → QUEUED，写确认主体、凭证哈希与执行截止时间。
     *
     * @param operationId 操作标识
     * @param userId 账号
     * @param expectedRowVersion 期望行版本
     * @param summaryHash 提交的摘要哈希
     * @param confirmationHash 一次性凭证哈希
     * @param executionDeadlineAt 执行截止时间
     * @param now 当前时间
     * @return 成功时返回 true
     */
    boolean confirm(String operationId, Long userId, long expectedRowVersion, byte[] summaryHash,
            byte[] confirmationHash, byte[] sessionHash, Instant executionDeadlineAt, Instant now);

    /**
     * 派发消费确认凭证：QUEUED → RUNNING 前清除凭证哈希并记录使用时间。
     *
     * @param operationId 操作标识
     * @param fence 当前围栏
     * @param now 当前时间
     * @return 成功时返回 true
     */
    boolean consumeConfirmation(String operationId, String owner, long fence, Instant now);

    /**
     * 领取一条到期的 QUEUED 操作，写入租约并递增围栏。
     *
     * @param owner 租约持有者
     * @param now 当前时间
     * @param leaseUntil 租约到期时间
     * @return 领取到的操作
     */
    Optional<McpOperationRecord> claimNextQueued(String owner, Instant now, Instant leaseUntil);

    /**
     * 领取到期的 UNKNOWN 或租约过期的 RUNNING 操作用于恢复。
     *
     * <p>只对可对账执行模型生效；不消费确认凭证、不回到 QUEUED。</p>
     *
     * @param owner 恢复租约持有者
     * @param now 当前时间
     * @param leaseUntil 新租约到期时间
     * @return 领取到的操作
     */
    Optional<McpOperationRecord> claimNextRecoverable(String owner, Instant now, Instant leaseUntil);

    /**
     * 为恢复操作设置下次尝试时间并释放租约，保持 UNKNOWN。
     *
     * @param operationId 操作标识
     * @param owner 租约持有者
     * @param fence 围栏
     * @param nextAttemptAt 下次尝试时间
     * @return 成功时返回 true
     */
    boolean deferRecovery(String operationId, String owner, long fence, Instant nextAttemptAt);

    /**
     * 恢复确证成功：UNKNOWN → SUCCEEDED 并写安全结果。
     *
     * @param operationId 操作标识
     * @param fence 围栏
     * @param resultCode 稳定结果码
     * @param encryptedResult 加密安全结果
     * @return 成功时返回 true
     */
    boolean recoverSucceeded(String operationId, String owner, long fence, String resultCode, String encryptedResult);

    /**
     * 恢复确证未生效：UNKNOWN → FAILED。
     *
     * @param operationId 操作标识
     * @param fence 围栏
     * @param resultCode 稳定结果码
     * @return 成功时返回 true
     */
    boolean recoverFailed(String operationId, String owner, long fence, String resultCode);

    /**
     * 心跳续租；匹配 owner、fence 与 RUNNING。
     *
     * @param operationId 操作标识
     * @param owner 租约持有者
     * @param fence 围栏
     * @param leaseUntil 新到期时间
     * @return 成功时返回 true
     */
    boolean heartbeat(String operationId, String owner, long fence, Instant leaseUntil);

    /**
     * 发送前检查点：在当前事务中记录 ledger 的 SEND_INTENT，要求仍持有有效租约。
     *
     * @param operationId 操作标识
     * @param owner worker 所有者
     * @param fence 围栏
     * @param stage 发送阶段
     * @return 成功时返回 true
     */
    boolean checkpointExternalSendIntent(String operationId, String owner, long fence, String stage);

    /**
     * owner/fence/有效租约下推进非终态阶段，并与领域 publication 回执同事务。
     *
     * @param operationId 操作标识
     * @param owner worker
     * @param fence 围栏
     * @param fromStage 期望阶段
     * @param toStage 新阶段
     * @return 成功时返回 true
     */
    boolean updateStage(String operationId, String owner, long fence, String fromStage, String toStage);

    /**
     * ledger 与 publication 回执调用方事务中推进 ledger 阶段；配合领域回执 Mapper 使用。
     *
     * @param operationId 操作标识
     * @param owner worker
     * @param fence 围栏
     * @return 成功时返回 true
     */
    boolean markRemoteCommittedStage(String operationId, String owner, long fence);

    /**
     * 发送回执后在同一事务内推进 ledger 阶段，并要求原 lease 仍有效。
     *
     * @param operationId 操作标识
     * @param owner worker
     * @param fence 围栏
     * @param expectedStage 期望旧阶段
     * @return 成功时返回 true
     */
    boolean markRemoteCommittedStage(String operationId, String owner, long fence, String expectedStage);

    /**
     * 对账成功时 ledger 阶段推进；仅恢复 owner/fence/有效租约可调用。
     *
     * @param operationId 操作标识
     * @param owner 恢复 worker
     * @param fence 当前围栏
     * @return 成功时返回 true
     */
    boolean reconcileRemoteCommittedStage(String operationId, String owner, long fence);

    /**
     * 批量续租当前进程持有的全部 RUNNING 操作。
     *
     * @param owner 租约持有者
     * @param leaseUntil 新到期时间
     * @return 续租条数
     */
    int heartbeatOwner(String owner, Instant leaseUntil);

    /**
     * 在匹配围栏的前提下推进状态与阶段，并写一条事件。
     *
     * @param operationId 操作标识
     * @param fence 期望围栏
     * @param from 期望当前状态
     * @param to 目标状态
     * @param stage 目标阶段
     * @param resultCode 稳定结果码
     * @param encryptedResult 加密后的安全结果，可为 {@code null}
     * @param eventType 固定事件类型
     * @param requestId 请求标识
     * @param owner 当前租约持有者；执行态终态写入必须与账本一致
     * @return 推进成功时返回 true
     */
    boolean transition(String operationId, long fence, String from, String to, String stage, String resultCode,
            String encryptedResult, String eventType, String requestId, String owner);

    /**
     * 只对确定尚未产生外部效果的失败回到 QUEUED。
     *
     * @param operationId 操作标识
     * @param fence 期望围栏
     * @param nextAttemptAt 下次尝试时间
     * @return 成功时返回 true
     */
    boolean releaseToQueued(String operationId, String owner, long fence, Instant nextAttemptAt);

    /** 在业务事务中锁定当前有效执行许可；锁持有到业务与账本一起提交。 */
    boolean lockForExecution(String operationId, String owner, long fence);

    /** 按本人及行版本取消准备态操作，与确认并发时只有一个动作成功。 */
    boolean cancelPrepared(String operationId, Long userId, long rowVersion);

    /**
     * 读取并解密规范化请求；调用方必须已完成授权复核。
     *
     * @param operationId 操作标识
     * @return 明文请求；不存在时返回 {@code null}
     */
    String decryptedRequest(String operationId);

    /**
     * 读取并解密安全结果；调用方必须已完成原工具权限复核。
     *
     * @param operationId 操作标识
     * @return 明文结果；不存在时返回 {@code null}
     */
    String decryptedResult(String operationId);

    /**
     * 清理已过保留期的已知终态账本与事件；UNKNOWN/RUNNING 不清理。
     *
     * @param now 当前时间
     * @param limit 单批上限
     * @return 删除的账本数量
     */
    int cleanupExpired(Instant now, int limit);

    /**
     * 清理已过保留期的已知终态账本与事件；存在未完成后续步骤的父操作跳过。
     *
     * @param now 当前时间
     * @param limit 单批上限
     * @param hasUnfinishedSteps 父操作是否仍有非终态步骤
     * @return 删除的账本数量
     */
    int cleanupExpired(Instant now, int limit, java.util.function.Predicate<String> hasUnfinishedSteps);
}
