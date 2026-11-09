package com.howe.mcp.service.security;

import com.howe.mcp.service.McpAuditService;
import com.howe.mcp.service.McpAuditService.Kind;
import com.howe.mcp.service.McpAuditService.Result;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** 独立链最外层审计；只复制白名单事实，状态事务与响应完成之后才提交辅助日志。 */
@Slf4j
@RequiredArgsConstructor
public final class McpAuditFilter extends OncePerRequestFilter {
    private final McpAuditService audit;

    /** 记录同步处理结果；SSE 只记接纳，实际工具耗时由工具观察器独立记录。 */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        McpAuditContext.begin();
        captureBrowserUser();
        long started = System.nanoTime();
        boolean completed = false;
        try {
            chain.doFilter(request, response);
            completed = true;
        } finally {
            try {
                captureBrowserUser();
                Object proof = request.getAttribute(McpBearerIdentity.REQUEST_ATTRIBUTE);
                if (proof instanceof McpBearerIdentity bearer) {
                    McpAuditContext.verified(bearer.invocation().userId(), null, bearer.invocation().grantId());
                }
                var facts = McpAuditContext.facts();
                Kind kind = kind(request);
                if (kind == null && response.getStatus() >= 400) { kind = Kind.PROTOCOL; }
                if (kind != null && (!"OPTIONS".equals(request.getMethod()) || response.getStatus() >= 400)) {
                    Result result = !completed ? Result.INTERNAL_ERROR : httpResult(response.getStatus(), kind, facts.result());
                    audit.record(new McpAuditService.Event(facts.requestId(), kind, facts.userId(), facts.registeredClientId(),
                            facts.grantId(), null, result,
                            Math.max(0, java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)), Instant.now()));
                }
            } finally { McpAuditContext.clear(); }
        }
    }

    /** 仅浏览器链使用的最小数字主体可归属账号；客户端 Authentication 和任意请求 userId 均忽略。 */
    private static void captureBrowserUser() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof UsernamePasswordAuthenticationToken && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof String value && value.matches("[1-9][0-9]{0,18}")) {
            try { McpAuditContext.verified(Long.valueOf(value), null, null); }
            catch (NumberFormatException exception) { /* 非法主键不参与审计归属。 */ }
        }
    }

    /** 不存原始路径，查询串、授权码和回调地址从不进入日志事件。 */
    private static Kind kind(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return switch (path) {
            case "/mcp" -> Kind.PROTOCOL;
            case "/oauth2/authorize" -> Kind.AUTHORIZE;
            case "/oauth2/token" -> Kind.TOKEN_EXCHANGE;
            case "/oauth2/revoke" -> Kind.TOKEN_REVOKE;
            case "/oauth2/register" -> Kind.CLIENT_REGISTER;
            case "/oauth2/login" -> Kind.LOGIN;
            case "/oauth2/self/logout" -> Kind.LOGOUT;
            default -> path.matches("/oauth2/self/connections/[1-9][0-9]*/revoke") ? Kind.GRANT_REVOKE : null;
        };
    }

    /** HTTP 错误优先，避免事务失败或拒绝响应被早期成功标记覆盖。 */
    private static Result httpResult(int status, Kind kind, Result verified) {
        if (status >= 500) { return Result.UNAVAILABLE; }
        if (status == 429) { return Result.RATE_LIMITED; }
        if (status == 401) { return Result.ACCESS_DENIED; }
        if (status == 403) { return Result.ACCESS_DENIED; }
        if (verified != null) { return verified; }
        if (status >= 400) { return Result.INVALID_REQUEST; }
        if (kind == Kind.PROTOCOL) { return Result.ACCEPTED; }
        if (kind == Kind.AUTHORIZE && status >= 300) { return Result.PENDING; }
        return Result.OK;
    }

}
