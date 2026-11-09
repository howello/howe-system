package com.howe.mcp.service.security;

import com.howe.mcp.service.McpTokenRevocationService;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2TokenRevocationAuthenticationToken;

import java.util.Objects;

/** 保留原生 RFC 7009 HTTP converter/响应处理，只替换撤销的归属与持久化策略。 */
public final class McpTokenRevocationAuthenticationProvider implements AuthenticationProvider {
    private final McpTokenRevocationService revocations;

    /** 撤销只信任客户端认证结果，不能采用表单里的另一个 client_id。 */
    public McpTokenRevocationAuthenticationProvider(McpTokenRevocationService revocations) {
        this.revocations = Objects.requireNonNull(revocations);
    }

    /** 未知或重复撤销同样成功，token_type_hint 只是提示，不改变实际类型查找。 */
    @Override
    public Authentication authenticate(Authentication authentication) {
        var request = (OAuth2TokenRevocationAuthenticationToken) authentication;
        if (!(request.getPrincipal() instanceof OAuth2ClientAuthenticationToken client)
                || !client.isAuthenticated() || client.getRegisteredClient() == null) {
            throw new OAuth2AuthenticationException(new OAuth2Error("invalid_client"));
        }
        McpAuditContext.verified(null, client.getRegisteredClient().getId(), null);
        revocations.revoke(client.getRegisteredClient().getId(), request.getToken());
        request.setAuthenticated(true);
        return request;
    }

    /** 只替换撤销请求 provider，不接管授权码、刷新或普通账号认证。 */
    @Override
    public boolean supports(Class<?> authentication) {
        return OAuth2TokenRevocationAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
