package com.howe.mcp.service;

import com.howe.mcp.service.security.McpInvocationContext;
import java.time.Instant;

/** 有界辅助审计服务接口。 */
public interface McpAuditService extends AutoCloseable {
    /** 固定事件种类，不使用原始路径或用户输入作为审计分类。 */
    public enum Kind { AUTHORIZE, TOKEN_EXCHANGE, TOKEN_REVOKE, GRANT_REVOKE, CLIENT_REGISTER, LOGIN, LOGOUT, PROTOCOL, TOOL_CALL }

    /** 固定结果代码，不接受外部异常或工具正文生成日志内容。 */
    public enum Result {
        OK, ACCEPTED, PENDING, ACCESS_DENIED, INVALID_REQUEST, INVALID_ARGUMENTS, INVALID_TOKEN, INVALID_CLIENT,
        INVALID_GRANT, INVALID_SCOPE, RATE_LIMITED, UNAVAILABLE, TOOL_UNAVAILABLE, SERVER_BUSY, USER_BUSY, TOOL_TIMEOUT,
        REQUEST_CANCELLED, RESULT_TOO_LARGE, INVALID_RESULT, EXECUTION_DENIED, TOOL_ERROR, INTERNAL_ERROR;

        /** 仅保留代码白名单，未知错误归入固定类别。 */
        public static Result fromCode(String code) {
            if (code == null) { return INTERNAL_ERROR; }
            return switch (code) {
                case "access_denied" -> ACCESS_DENIED;
                case "invalid_request" -> INVALID_REQUEST;
                case "invalid_target", "unsupported_grant_type", "unsupported_response_type", "invalid_client_metadata" -> INVALID_REQUEST;
                case "server_error" -> INTERNAL_ERROR;
                case "invalid_arguments" -> INVALID_ARGUMENTS;
                case "invalid_token" -> INVALID_TOKEN;
                case "invalid_client" -> INVALID_CLIENT;
                case "invalid_grant" -> INVALID_GRANT;
                case "invalid_scope" -> INVALID_SCOPE;
                case "rate_limit_exceeded" -> RATE_LIMITED;
                case "temporarily_unavailable" -> UNAVAILABLE;
                case "tool_unavailable" -> TOOL_UNAVAILABLE;
                case "server_busy" -> SERVER_BUSY;
                case "user_busy" -> USER_BUSY;
                case "tool_timeout" -> TOOL_TIMEOUT;
                case "request_cancelled" -> REQUEST_CANCELLED;
                case "result_too_large" -> RESULT_TOO_LARGE;
                case "invalid_result" -> INVALID_RESULT;
                case "execution_denied", "identity_missing" -> EXECUTION_DENIED;
                default -> TOOL_ERROR;
            };
        }
    }

    /** 不含任意摘要字段的不可变事件；client ID 为服务端登记主键，禁止传入外部 client_id。 */
    public record Event(String requestId, Kind kind, Long userId, String registeredClientId, Long grantId,
            String toolName, Result result, long elapsedMs, Instant occurredAt) { }

    /** 工具事件只复制已认证上下文中的主键，不让队列持有完整账号或模型参数。 */
    void tool(McpInvocationContext invocation, String knownName, Result result, long elapsedMs);

    /** 提交只执行有界内存操作，队列饱和或服务关闭不阻塞业务线程。 */
    void record(Event event);

    /** 提供明确的积压、失败和丢弃指标，供管理监控使用。 */
    Statistics statistics();

    /** 不包含事件正文或账号信息的审计运行指标。 */
    public record Statistics(long submitted, long written, long failed, long dropped, int queued, int active) { }

    /** 关闭时最多等待两秒排空，之后中断工作并记录未执行数量，不无界阻塞应用停止。 */
    void close();
}
