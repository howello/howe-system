package com.howe.mcp.operation;

import java.time.Instant;

/**
 * 父操作后续步骤的账本记录。
 *
 * <p>步骤只保存代码键、状态、加密载荷与租约信息；授权绑定一律通过不可变父操作引用取得，
 * 不复制 token、grant 密文或客户端信息。</p>
 *
 * @param stepId 步骤标识
 * @param operationId 父操作标识
 * @param stepKey 步骤代码键
 * @param handlerVersion 登记的处理器版本
 * @param state 状态
 * @param stage 阶段
 * @param resultCode 稳定结果码
 * @param encryptedPayload 加密载荷
 * @param encryptedReceipt 加密回执
 * @param encryptedResult 加密安全结果
 * @param leaseOwner 租约持有者
 * @param leaseUntil 租约到期
 * @param fence 执行围栏
 * @param attempt 已尝试次数
 * @param nextAttemptAt 下次可尝试时间
 * @param executionDeadlineAt 执行截止时间
 * @param createTime 创建时间
 * @param updateTime 更新时间
 */
public record McpOperationStepRecord(String stepId, String operationId, String stepKey, String handlerVersion,
        String state, String stage, String resultCode, String encryptedPayload, String encryptedReceipt,
        String encryptedResult, String leaseOwner, Instant leaseUntil, long fence, int attempt,
        Instant nextAttemptAt, Instant executionDeadlineAt, Instant createTime, Instant updateTime) {
}
