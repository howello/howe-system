package com.howe.common.tool.operation;

import java.net.URI;
import java.time.Instant;
import java.util.Map;

/**
 * 不含凭证、正文、内部租约和来源路径的通用回执。
 *
 * <p>由平台投影给工具与浏览器；{@code safeResult} 必须按工具白名单构造，
 * 不能直接序列化实体或解密对象。</p>
 *
 * @param operationId 操作标识；预览为 {@code null}
 * @param state 对外状态
 * @param stage 领域阶段
 * @param confirmationRequired 是否需要浏览器确认
 * @param confirmationUrl 固定站点确认地址
 * @param prepareExpiresAt 准备到期时间
 * @param createdAt 创建时间
 * @param finishedAt 完成时间
 * @param resultCode 稳定结果码
 * @param summary 安全摘要
 * @param safeResult 安全结果
 * @param retryAfterMs 建议的下次查询间隔
 */
public record ToolOperationReceipt(String operationId, State state, String stage, boolean confirmationRequired,
        URI confirmationUrl, Instant prepareExpiresAt, Instant createdAt, Instant finishedAt, String resultCode,
        Map<String, Object> summary, Map<String, Object> safeResult, Long retryAfterMs) {

    /** 对外状态；PREVIEW 仅为即时回执，不进入账本。 */
    public enum State {
        /** 即时预览。 */
        PREVIEW,
        /** 待确认。 */
        PREPARED,
        /** 已确认。 */
        CONFIRMED,
        /** 已排队。 */
        QUEUED,
        /** 执行中。 */
        RUNNING,
        /** 成功。 */
        SUCCEEDED,
        /** 失败。 */
        FAILED,
        /** 结果未知。 */
        UNKNOWN,
        /** 用户取消。 */
        CANCELLED,
        /** 已过期。 */
        EXPIRED
    }

    /** 防御性复制摘要与结果。 */
    public ToolOperationReceipt {
        summary = ToolOperationValues.copyMap(summary);
        safeResult = safeResult == null ? null : ToolOperationValues.copyMap(safeResult);
    }
}
