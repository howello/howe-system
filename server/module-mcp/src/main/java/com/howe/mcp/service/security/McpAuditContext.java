package com.howe.mcp.service.security;

import cn.hutool.core.util.IdUtil;
import com.howe.mcp.service.McpAuditService.Result;

/** 同步 HTTP 边界内的最小审计事实；不存 token、state、请求或完整 Authentication。 */
public final class McpAuditContext {
    private static final ThreadLocal<Facts> CURRENT = new ThreadLocal<>();

    /** 工具类不实例化。 */
    private McpAuditContext() { }

    /** 每个请求由最外层审计过滤器开始，不能继承线程池之前请求的事实。 */
    public static void begin() { CURRENT.set(new Facts(IdUtil.fastSimpleUUID(), null, null, null, null)); }

    /** 只在已验证归属后设置服务端主键；空字段保留本次已验证的旧值。 */
    public static void verified(Long userId, String registeredClientId, Long grantId) {
        Facts current = CURRENT.get();
        if (current != null) {
            CURRENT.set(new Facts(current.requestId(), userId == null ? current.userId() : userId,
                    registeredClientId == null ? current.registeredClientId() : registeredClientId,
                    grantId == null ? current.grantId() : grantId, current.result()));
        }
    }

    /** 标记安全校验后的固定协议结果，特别是以 302 回调返回的用户拒绝。 */
    public static void result(Result result) {
        Facts current = CURRENT.get();
        if (current != null) { CURRENT.set(new Facts(current.requestId(), current.userId(), current.registeredClientId(), current.grantId(), result)); }
    }

    /** Bearer 与工具执行使用同一服务端请求号，不读取外部请求号。 */
    public static String requestId() { return CURRENT.get() == null ? IdUtil.fastSimpleUUID() : CURRENT.get().requestId(); }

    /** 读取不可变最小快照，只供同步边界结束时复制入队。 */
    public static Facts facts() { return CURRENT.get(); }

    /** 正常、拒绝、异常和异步接纳路径均必须在 finally 清理。 */
    public static void clear() { CURRENT.remove(); }

    /** 归属必须来自已认证的 Session、Bearer 或已校验客户端与授权存储。 */
    public record Facts(String requestId, Long userId, String registeredClientId, Long grantId, Result result) { }
}
