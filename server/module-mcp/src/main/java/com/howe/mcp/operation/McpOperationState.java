package com.howe.mcp.operation;

/** 操作账本状态常量，与数据库检查约束保持一致。 */
public final class McpOperationState {

    /** 待浏览器确认。 */
    public static final String PREPARED = "PREPARED";
    /** 已确认（逻辑阶段，正常与 QUEUED 同事务完成）。 */
    public static final String CONFIRMED = "CONFIRMED";
    /** 已入队。 */
    public static final String QUEUED = "QUEUED";
    /** 已领取执行。 */
    public static final String RUNNING = "RUNNING";
    /** 成功。 */
    public static final String SUCCEEDED = "SUCCEEDED";
    /** 失败或确知未生效。 */
    public static final String FAILED = "FAILED";
    /** 已发送但结果未知。 */
    public static final String UNKNOWN = "UNKNOWN";
    /** 用户拒绝。 */
    public static final String CANCELLED = "CANCELLED";
    /** 已过期。 */
    public static final String EXPIRED = "EXPIRED";

    /** 常量类不允许实例化。 */
    private McpOperationState() {
    }
}
