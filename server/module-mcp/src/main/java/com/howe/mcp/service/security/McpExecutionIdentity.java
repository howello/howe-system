package com.howe.mcp.service.security;

import com.howe.common.core.domain.model.LoginUser;
import com.howe.mcp.service.spi.McpAccountBridge;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

/** 在 SDK 实际工具线程重新加载账号并绑定项目身份；调用结束恢复原线程上下文。 */
public final class McpExecutionIdentity {
    public static final String TRANSPORT_KEY = "howe.mcp.invocation";
    private final McpAccountBridge accounts;

    /** 注入能按用户 ID 加载当前权限的桥接器。 */
    public McpExecutionIdentity(McpAccountBridge accounts) {
        this.accounts = Objects.requireNonNull(accounts);
    }

    /**
     * 在当前账号身份下执行已通过授权复核的工具；不信任令牌签发时的权限快照。
     *
     * @param invocation 从已认证 HTTP 请求传入的调用身份
     * @param action 工具操作，内部可使用 SecurityUtils
     * @return 操作结果
     * @param <T> 结果类型
     */
    public <T> T execute(McpInvocationContext invocation, Supplier<T> action) {
        Objects.requireNonNull(invocation);
        Objects.requireNonNull(action);
        LoginUser current = accounts.loadActiveUser(invocation.userId());
        if (current == null || invocation.userId() == null
                || !invocation.userId().equals(current.getUserId()) || current.getUser() == null) {
            throw new IllegalStateException("无法加载当前 MCP 用户身份");
        }
        SecurityContext previous = SecurityContextHolder.getContext();
        SecurityContext execution = SecurityContextHolder.createEmptyContext();
        execution.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(current, null, List.of()));
        SecurityContextHolder.setContext(execution);
        try {
            return action.get();
        } finally {
            SecurityContextHolder.clearContext();
            if (previous.getAuthentication() != null) {
                SecurityContextHolder.setContext(previous);
            }
        }
    }
}
