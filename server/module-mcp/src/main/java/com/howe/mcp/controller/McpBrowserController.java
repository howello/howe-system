package com.howe.mcp.controller;

import com.howe.common.annotation.Anonymous;
import com.howe.common.annotation.RateLimiter;
import com.howe.common.core.domain.model.LoginUser;
import com.howe.common.enums.LimitType;
import com.howe.common.exception.ServiceException;
import com.howe.mcp.config.McpBrowserState;
import com.howe.mcp.dto.McpLoginRequest;
import com.howe.mcp.service.spi.McpAccountBridge;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 独立 OAuth 浏览器账号入口，不读写管理端 JWT、菜单或角色 store。 */
@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "MCP 独立账号会话")
@ConditionalOnProperty(prefix = "howe.mcp", name = "installed", havingValue = "true")
public class McpBrowserController {
    private final McpAccountBridge accounts;
    private final McpBrowserState state;

    /** 建立短期会话及 CSRF，不返回任何用户授权记录或凭据。 */
    @Anonymous
    @RateLimiter(count = 60, time = 60, limitType = LimitType.IP)
    @Operation(summary = "获取 MCP 登录 CSRF 上下文")
    @GetMapping("/oauth2/login/context")
    public Map<String, String> loginContext(HttpServletRequest request) {
        CsrfToken csrf = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        return Map.of("csrfToken", csrf.getToken(), "csrfHeaderName", csrf.getHeaderName(), "csrfParameterName", csrf.getParameterName());
    }

    /** 保留账号校验并轮换会话 ID 和 CSRF；身份只保存为稳定用户主键。 */
    @Anonymous
    @RateLimiter(count = 10, time = 60, limitType = LimitType.IP)
    @Operation(summary = "独立登录 MCP 账号会话")
    @PostMapping("/oauth2/login")
    public SessionView login(@Valid @RequestBody McpLoginRequest body, HttpServletRequest request, HttpServletResponse response) {
        LoginUser user = accounts.authenticate(body.toLoginBody());
        if (user == null || user.getUserId() == null || user.getUserId() <= 0 || user.getUser() == null) {
            throw new ServiceException("账号认证失败");
        }
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(String.valueOf(user.getUserId()), null, List.of());
        new ChangeSessionIdAuthenticationStrategy().onAuthentication(authentication, request, response);
        new CsrfAuthenticationStrategy(state.csrf()).onAuthentication(authentication, request, response);
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        state.contexts().saveContext(context, request, response);
        return new SessionView(true, user.getUsername());
    }

    /** 从会话主键刷新当前账号，不向页面提供权限表或密码摘要。 */
    @RateLimiter(count = 120, time = 60, limitType = LimitType.IP)
    @Operation(summary = "获取当前 MCP 独立会话")
    @GetMapping("/oauth2/self/session")
    public SessionView session(Authentication authentication) {
        LoginUser user = accounts.loadActiveUser(userId(authentication));
        return new SessionView(true, user.getUsername());
    }

    /** 只结束 OAuth 浏览器会话；授权撤销和管理端退出是独立操作。 */
    @RateLimiter(count = 20, time = 60, limitType = LimitType.IP)
    @Operation(summary = "退出 MCP 独立浏览器会话")
    @PostMapping("/oauth2/self/logout")
    public Map<String, Boolean> logout(HttpServletRequest request) {
        var session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return Map.of("loggedOut", true);
    }

    /** 只从已认证主体读取主键，不接收请求中的 userId。 */
    public static Long userId(Authentication authentication) {
        try {
            long value = Long.parseLong(authentication.getName());
            if (!authentication.isAuthenticated() || value <= 0 || !String.valueOf(value).equals(authentication.getName())) {
                throw new IllegalArgumentException();
            }
            return value;
        } catch (RuntimeException exception) {
            throw new org.springframework.security.access.AccessDeniedException("OAuth 浏览器会话不可用");
        }
    }

    /** 页面需要的最小账号显示信息。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "MCP 独立会话状态")
    public record SessionView(boolean authenticated, String username) { }
}
