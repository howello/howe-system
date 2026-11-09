package com.howe.mcp.service.security;

import com.howe.mcp.service.spi.McpClientAuthorizationLookup;
import java.util.Objects;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

/**
 * 通过已签发刷新令牌的归属确认公共客户端；实际兑换与范围校验仍由授权服务器处理。
 * 持久化授权服务还必须负责当前授权状态、轮换重放和事务并发控制。
 */
public final class McpPublicRefreshAuthenticationProvider implements AuthenticationProvider {
    private final RegisteredClientRepository clients;
    private final OAuth2AuthorizationService authorizations;

    /** 注入客户端登记和授权存储，不缓存当前授权状态。 */
    public McpPublicRefreshAuthenticationProvider(RegisteredClientRepository clients,
            OAuth2AuthorizationService authorizations) {
        this.clients = Objects.requireNonNull(clients);
        this.authorizations = Objects.requireNonNull(authorizations);
    }

    /** 校验客户端支持公共刷新，且提交令牌属于该客户端及有效的离线授权。 */
    @Override
    public Authentication authenticate(Authentication authentication) {
        OAuth2ClientAuthenticationToken request = (OAuth2ClientAuthenticationToken) authentication;
        if (!ClientAuthenticationMethod.NONE.equals(request.getClientAuthenticationMethod())
                || !AuthorizationGrantType.REFRESH_TOKEN.getValue()
                        .equals(request.getAdditionalParameters().get("grant_type"))) {
            return null;
        }
        RegisteredClient client = clients.findByClientId(request.getPrincipal().toString());
        if (client == null || !client.getClientAuthenticationMethods().contains(ClientAuthenticationMethod.NONE)
                || !client.getAuthorizationGrantTypes().contains(AuthorizationGrantType.REFRESH_TOKEN)) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_CLIENT);
        }
        Object token = request.getAdditionalParameters().get("refresh_token");
        if (!(token instanceof String value) || value.isBlank()) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_REQUEST);
        }
        // 本模块持久化查找包含重放失效，必须在副作用之前限定客户端；框架内存仓库只是纯读取。
        OAuth2Authorization authorization = authorizations instanceof McpClientAuthorizationLookup scoped
                ? scoped.findByTokenForClient(value, OAuth2TokenType.REFRESH_TOKEN, client.getId())
                : authorizations.findByToken(value, OAuth2TokenType.REFRESH_TOKEN);
        if (authorization == null || !client.getId().equals(authorization.getRegisteredClientId())
                || !authorization.getAuthorizedScopes().contains(McpScopes.OFFLINE_ACCESS)
                || authorization.getRefreshToken() == null || !authorization.getRefreshToken().isActive()) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_GRANT);
        }
        return new OAuth2ClientAuthenticationToken(client, ClientAuthenticationMethod.NONE, null);
    }

    /** 与框架其它客户端认证 provider 共用令牌类型，以认证方式与 grant 分流。 */
    @Override
    public boolean supports(Class<?> authentication) {
        return OAuth2ClientAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
