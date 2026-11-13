package com.howe.mcp.operation;

import java.time.Instant;

/**
 * 操作账本的最小内存投影。
 *
 * <p>只包含平台内部派发、确认与查询需要的字段；不包含加密载荷正文、确认凭证原文
 * 或任何令牌。加密请求/结果由存储层按需单独解密，避免敏感内容在对象间传播。</p>
 *
 * @param operationId 操作标识
 * @param userId 操作账号
 * @param registeredClientId 内部客户端登记主键
 * @param grantId 原授权主键
 * @param grantVersion 准备时的授权版本
 * @param resourceUri 授权资源
 * @param approvedScopes 准备时已批准的范围，逗号分隔
 * @param preparedDeptId 准备时的部门
 * @param accountContextHash 账号上下文摘要
 * @param toolName 原工具名
 * @param toolVersion 原工具版本
 * @param executionKind 执行模型
 * @param queueGroup 队列分组
 * @param idempotencyHash 幂等键哈希
 * @param argumentsHash 规范化参数哈希
 * @param configurationHash 目标配置哈希
 * @param targetType 目标类型
 * @param targetId 目标主键
 * @param expectedVersion 目标版本
 * @param state 当前状态
 * @param stage 当前阶段
 * @param confirmedBy 确认账号
 * @param confirmedAt 确认时间
 * @param confirmationExpiresAt 确认上下文到期时间
 * @param prepareExpiresAt 准备到期时间
 * @param executionDeadlineAt 执行截止时间
 * @param nextAttemptAt 下次可尝试时间
 * @param leaseOwner 租约持有者
 * @param leaseUntil 租约到期时间
 * @param fence 围栏令牌
 * @param attempt 已尝试次数
 * @param resultCode 稳定结果码
 * @param resultBytes 结果字节数
 * @param rowVersion 行版本
 * @param payloadRetainUntil 加密载荷保留截止时间
 * @param retainUntil 元数据保留截止时间
 * @param finishedAt 完成时间
 * @param createdAt 创建时间
 * @param updatedAt 更新时间
 */
public record McpOperationRecord(String operationId, Long userId, String registeredClientId, Long grantId,
        long grantVersion, String resourceUri, String approvedScopes, Long preparedDeptId, byte[] accountContextHash,
        String toolName, String toolVersion, String executionKind, String queueGroup, byte[] idempotencyHash,
        byte[] argumentsHash, byte[] configurationHash, String targetType, String targetId, String expectedVersion,
        String state, String stage, Long confirmedBy, Instant confirmedAt, Instant confirmationExpiresAt,
        Instant prepareExpiresAt, Instant executionDeadlineAt, Instant nextAttemptAt, String leaseOwner,
        Instant leaseUntil, long fence, int attempt, String resultCode, Integer resultBytes, long rowVersion,
        Instant payloadRetainUntil, Instant retainUntil, Instant finishedAt, Instant createdAt, Instant updatedAt) {

    /** 防御性复制哈希，避免调用方修改账本标识。 */
    public McpOperationRecord {
        accountContextHash = accountContextHash == null ? null : accountContextHash.clone();
        idempotencyHash = idempotencyHash == null ? null : idempotencyHash.clone();
        argumentsHash = argumentsHash == null ? null : argumentsHash.clone();
        configurationHash = configurationHash == null ? null : configurationHash.clone();
    }
}
