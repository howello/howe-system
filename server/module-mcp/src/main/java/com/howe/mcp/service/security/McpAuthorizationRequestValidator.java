package com.howe.mcp.service.security;

import com.howe.mcp.config.McpEndpointSettings;
import java.util.Objects;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationContext;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationException;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationToken;

/** 在原生授权码 provider 中替换默认参数 validator，限制本平台实际支持的授权能力。 */
@Slf4j
public final class McpAuthorizationRequestValidator
        implements Consumer<OAuth2AuthorizationCodeRequestAuthenticationContext> {
    private final String resource;

    /** 资源固定为启动时校验的公共地址，不能由请求 Host 或客户端指定新受众。 */
    public McpAuthorizationRequestValidator(McpEndpointSettings endpoints) {
        this.resource = Objects.requireNonNull(endpoints).resource();
    }

    /** 先校验回调，再校验范围、资源与 S256；回调无效时禁止外部重定向。 */
    @Override
    public void accept(OAuth2AuthorizationCodeRequestAuthenticationContext context) {
        OAuth2AuthorizationCodeRequestAuthenticationToken request = context.getAuthentication();
        var client = context.getRegisteredClient();
        String redirect = request.getRedirectUri();
        if (redirect == null || client.getRedirectUris().stream()
                .noneMatch(registered -> McpUriPolicy.redirectMatches(registered, redirect))) {
            throw failure(request, "invalid_request", "回调地址无效", false);
        }
        if (!request.getScopes().contains(McpScopes.CONNECT)
                || !McpScopes.SUPPORTED.containsAll(request.getScopes())
                || !client.getScopes().containsAll(request.getScopes())
                || (request.getScopes().contains(McpScopes.OFFLINE_ACCESS)
                        && !client.getAuthorizationGrantTypes().contains(AuthorizationGrantType.REFRESH_TOKEN))) {
            throw failure(request, "invalid_scope", "授权范围无效", true);
        }
        if (!resource.equals(request.getAdditionalParameters().get("resource"))) {
            throw failure(request, "invalid_target", "必须指定当前 MCP 资源", true);
        }
        Object challenge = request.getAdditionalParameters().get("code_challenge");
        if (!"S256".equals(request.getAdditionalParameters().get("code_challenge_method"))
                || !(challenge instanceof String value) || !value.matches("[A-Za-z0-9_-]{43}")) {
            throw failure(request, "invalid_request", "必须使用有效的 PKCE S256", true);
        }
    }

    /** 只把已通过精确登记匹配的回调传给框架失败处理器。 */
    private static OAuth2AuthorizationCodeRequestAuthenticationException failure(
            OAuth2AuthorizationCodeRequestAuthenticationToken request, String code, String description,
            boolean redirectVerified) {
        var result = new OAuth2AuthorizationCodeRequestAuthenticationToken(request.getAuthorizationUri(),
                request.getClientId(), (Authentication) request.getPrincipal(),
                redirectVerified ? request.getRedirectUri() : null, request.getState(), request.getScopes(),
                request.getAdditionalParameters());
        result.setAuthenticated(redirectVerified);
        return new OAuth2AuthorizationCodeRequestAuthenticationException(new OAuth2Error(code, description, null), result);
    }
}
