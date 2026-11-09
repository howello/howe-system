package com.howe.mcp.service.security;

import com.howe.mcp.config.McpEndpointSettings;
import java.util.List;
import java.util.Objects;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenClaimsContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;

/** 将不透明访问令牌的声明受众固定到已批准资源，而非框架默认的 client_id。 */
public final class McpAccessTokenCustomizer implements OAuth2TokenCustomizer<OAuth2TokenClaimsContext> {
    private final String resource;

    /** 使用协议启动快照中的资源地址。 */
    public McpAccessTokenCustomizer(McpEndpointSettings endpoints) {
        this.resource = Objects.requireNonNull(endpoints).resource();
    }

    /** 签发前复核已批准资源与范围，再设置标准 audience。 */
    @Override
    public void customize(OAuth2TokenClaimsContext context) {
        if (context.getAuthorization() == null
                || !resource.equals(context.getAuthorization().getAttribute(McpOAuthAttributes.RESOURCE))) {
            throw new OAuth2AuthenticationException(new OAuth2Error("invalid_target", "授权资源不匹配", null));
        }
        if (!context.getAuthorizedScopes().contains(McpScopes.CONNECT)
                || !McpScopes.SUPPORTED.containsAll(context.getAuthorizedScopes())) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_SCOPE);
        }
        context.getClaims().audience(List.of(resource));
    }
}
