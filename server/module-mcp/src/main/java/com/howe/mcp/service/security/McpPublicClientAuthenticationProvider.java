package com.howe.mcp.service.security;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.PublicClientAuthenticationProvider;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

/** 按 grant 在同一个 provider 中分流公共客户端认证，避免失败后回退到另一套认证规则。 */
@lombok.extern.slf4j.Slf4j
public final class McpPublicClientAuthenticationProvider implements AuthenticationProvider {
    private final AuthenticationProvider codes;
    private final AuthenticationProvider refreshes;
    private final RegisteredClientRepository clients;

    /** 授权码继续采用框架 PKCE，刷新采用已验证的令牌归属扩展。 */
    public McpPublicClientAuthenticationProvider(RegisteredClientRepository clients, OAuth2AuthorizationService authorizations) {
        this.clients = java.util.Objects.requireNonNull(clients);
        codes = new PublicClientAuthenticationProvider(clients, authorizations);
        refreshes = new McpPublicRefreshAuthenticationProvider(clients, authorizations);
    }

    /** 机密客户端交给其专用 provider，不降级为 none。 */
    @Override
    public Authentication authenticate(Authentication authentication) {
        var client = (OAuth2ClientAuthenticationToken) authentication;
        if (!ClientAuthenticationMethod.NONE.equals(client.getClientAuthenticationMethod())) {
            return null;
        }
        if (client instanceof McpPublicRevocationClientToken) {
            var registered = clients.findByClientId(client.getPrincipal().toString());
            if (registered == null || !registered.getClientAuthenticationMethods().contains(ClientAuthenticationMethod.NONE)) {
                throw new OAuth2AuthenticationException(new OAuth2Error("invalid_client"));
            }
            return new OAuth2ClientAuthenticationToken(registered, ClientAuthenticationMethod.NONE, null);
        }
        return "refresh_token".equals(client.getAdditionalParameters().get("grant_type"))
                ? refreshes.authenticate(client) : codes.authenticate(client);
    }

    /** 与框架客户端认证过滤器使用相同令牌类型。 */
    @Override
    public boolean supports(Class<?> authentication) {
        return OAuth2ClientAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
