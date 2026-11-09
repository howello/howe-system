package com.howe.mcp.service.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.howe.mcp.config.McpEndpointSettings;
import com.howe.mcp.service.McpAuditService.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationException;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

/** 授权成功与可安全跳转的错误均携带固定 issuer；未验证的回调只收到本站 JSON 错误。 */
@Slf4j
public final class McpAuthorizationResponseHandler implements AuthenticationSuccessHandler, AuthenticationFailureHandler {
    private final String issuer;
    private final ObjectMapper mapper;
    private final RegisteredClientRepository clients;
    private final DefaultRedirectStrategy redirects = new DefaultRedirectStrategy();

    /** 响应只使用部署快照和已验证的框架认证结果，不从请求参数或 Host 读取地址。 */
    public McpAuthorizationResponseHandler(McpEndpointSettings endpoints, ObjectMapper mapper,
            RegisteredClientRepository clients) {
        this.issuer = Objects.requireNonNull(endpoints).issuer();
        this.mapper = Objects.requireNonNull(mapper);
        this.clients = Objects.requireNonNull(clients);
    }

    /** 保留既有回调查询参数，安全编码 code、state 和 RFC 9207 issuer。 */
    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException {
        noStore(response);
        if (!(authentication instanceof OAuth2AuthorizationCodeRequestAuthenticationToken result)
                || !result.isAuthenticated() || result.getAuthorizationCode() == null || !safeRedirect(result)) {
            jsonError(response, "server_error");
            return;
        }
        var target = UriComponentsBuilder.fromUriString(result.getRedirectUri())
                .queryParam("code", encode(result.getAuthorizationCode().getTokenValue()));
        addStateAndIssuer(target, result);
        redirects.sendRedirect(request, response, target.build(true).toUriString());
    }

    /** 不把框架异常描述、内部地址或原始请求回显给客户端。 */
    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) throws IOException {
        noStore(response);
        if (exception instanceof OAuth2AuthorizationCodeRequestAuthenticationException failure) {
            McpAuditContext.result(Result.fromCode(failure.getError().getErrorCode()));
            var result = failure.getAuthorizationCodeRequestAuthentication();
            String error = failure.getError().getErrorCode();
            if (result != null && safeRedirect(result)) {
                var target = UriComponentsBuilder.fromUriString(result.getRedirectUri())
                        .queryParam("error", encode(error));
                addStateAndIssuer(target, result);
                redirects.sendRedirect(request, response, target.build(true).toUriString());
                return;
            }
            jsonError(response, error);
            return;
        }
        McpAuditContext.result(Result.INTERNAL_ERROR);
        jsonError(response, "server_error");
    }

    /** 失败结果不一定经过自定义 validator，不能凭 authenticated 标记或非空地址决定跳转。 */
    private boolean safeRedirect(OAuth2AuthorizationCodeRequestAuthenticationToken result) {
        var client = clients.findByClientId(result.getClientId());
        return client != null && result.getRedirectUri() != null && client.getRedirectUris().stream()
                .anyMatch(registered -> McpUriPolicy.redirectMatches(registered, result.getRedirectUri()));
    }

    /** state 保持原值，URL 编码只做一次；issuer 始终来自服务端可信配置。 */
    private void addStateAndIssuer(UriComponentsBuilder target, OAuth2AuthorizationCodeRequestAuthenticationToken result) {
        if (result.getState() != null) {
            target.queryParam("state", encode(result.getState()));
        }
        target.queryParam("iss", encode(issuer));
    }

    /** 采用与框架原生处理器一致的 UTF-8 查询参数编码策略。 */
    private static String encode(String value) {
        return UriUtils.encode(value, StandardCharsets.UTF_8);
    }

    /** 认证结果不能被浏览器、代理或共享缓存保存。 */
    private static void noStore(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Pragma", "no-cache");
    }

    /** 没有安全回调时返回协议 JSON，不调用 sendError 触发容器 HTML 页面。 */
    private void jsonError(HttpServletResponse response, String code) throws IOException {
        response.setStatus("server_error".equals(code)
                ? HttpServletResponse.SC_INTERNAL_SERVER_ERROR : HttpServletResponse.SC_BAD_REQUEST);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        mapper.writeValue(response.getWriter(), Map.of("error", code));
    }
}
