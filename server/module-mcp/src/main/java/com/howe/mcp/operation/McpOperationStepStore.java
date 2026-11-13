package com.howe.mcp.operation;

import java.time.Instant;
import java.util.Optional;

/**
 * 后续步骤账本存储。
 *
 * <p>步骤与父操作同库；所有领取与写回都带 owner/fence/lease 条件，
 * 过期 worker 不得覆盖新状态。</p>
 */
public interface McpOperationStepStore {

    /**
     * 在父操作成功事务内登记步骤；同一父操作同一代码键只允许一条。
     *
     * @param stepId 步骤标识
     * @param operationId 父操作标识
     * @param stepKey 步骤代码键
     * @param handlerVersion 处理器版本
     * @param encryptedPayload 加密载荷
     * @param executionDeadlineAt 执行截止时间
     * @param now 当前时间
     * @return 新登记时返回 true；已存在返回 false
     */
    boolean insert(String stepId, String operationId, String stepKey, String handlerVersion,
            String encryptedPayload, Instant executionDeadlineAt, Instant now);

    /**
     * 领取下一条可执行步骤。
     *
     * @param owner 当前 worker
     * @param now 当前时间
     * @param leaseUntil 新租约到期
     * @return 已领取步骤；没有可执行项时返回空
     */
    Optional<McpOperationStepRecord> claimNext(String owner, Instant now, Instant leaseUntil);

    /**
     * 按父操作读取全部步骤，用于操作详情投影。
     *
     * @param operationId 父操作标识
     * @return 步骤列表，按创建顺序
     */
    java.util.List<McpOperationStepRecord> findByOperation(String operationId);

    /**
     * 在引用仍有效时推进步骤阶段。
     *
     * @param stepId 步骤标识
     * @param owner worker
     * @param fence 围栏
     * @param stage 新阶段
     * @return 成功时返回 true
     */
    /** 在当前事务中锁定仍有效的步骤执行许可。 */
    boolean lockForExecution(String stepId, String owner, long fence);

    boolean updateStage(String stepId, String owner, long fence, String stage);

    /**
     * 续租当前 worker 持有的全部非终态步骤。
     *
     * @param owner worker
     * @param leaseUntil 新到期时间
     * @return 续租条数
     */
    int heartbeatOwner(String owner, Instant leaseUntil);

    /**
     * 写入安全终态；只有当前 owner/fence 可写。
     *
     * @param stepId 步骤标识
     * @param owner worker
     * @param fence 围栏
     * @param state 终态
     * @param stage 阶段
     * @param resultCode 结果码
     * @param encryptedResult 加密安全结果
     * @return 成功时返回 true
     */
    boolean finish(String stepId, String owner, long fence, String state, String stage, String resultCode,
            String encryptedResult);

    /**
     * 结果未知或需要退避时释放租约并安排下次尝试。
     *
     * @param stepId 步骤标识
     * @param owner worker
     * @param fence 围栏
     * @param state 目标非终态
     * @param resultCode 结果码
     * @param nextAttemptAt 下次可尝试时间
     * @return 成功时返回 true
     */
    boolean defer(String stepId, String owner, long fence, String state, String resultCode, Instant nextAttemptAt);

    /**
     * 读取并解密步骤载荷。
     *
     * @param stepId 步骤标识
     * @return 明文载荷；不存在时返回 {@code null}
     */
    String decryptedPayload(String stepId);

    /**
     * 读取并解密步骤安全结果。
     *
     * @param stepId 步骤标识
     * @return 明文结果；不存在时返回 {@code null}
     */
    String decryptedResult(String stepId);

    /**
     * 判断父操作是否仍有非终态步骤，用于保留期清理。
     *
     * @param operationId 父操作标识
     * @return 存在非终态步骤时返回 true
     */
    boolean hasUnfinished(String operationId);
}
