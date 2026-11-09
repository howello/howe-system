package com.howe.mcp.service.security;

import com.howe.common.config.ConfigProvider;
import com.howe.common.constant.ConfigConstants;
import com.howe.mcp.service.spi.McpAccountBridge;
import com.howe.mcp.service.spi.McpClientAuthorizationLookup;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2RefreshTokenAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;

/** 兑换前刷新用户状态，不能只凭登录或首次签发时缓存的账号继续兑换。 */
@Slf4j
public final class McpTokenAccountAuthenticationProvider implements AuthenticationProvider {
    private final AuthenticationProvider delegate;
    private final OAuth2AuthorizationService authorizations;
    private final McpAccountBridge accounts;
    private final ConfigProvider config;

    /** 在资源和整笔兑换事务装饰器内部使用，不发布为全局认证入口。 */
    public McpTokenAccountAuthenticationProvider(AuthenticationProvider delegate, OAuth2AuthorizationService authorizations,
            McpAccountBridge accounts, ConfigProvider config) {
        this.delegate = Objects.requireNonNull(delegate);
        this.authorizations = Objects.requireNonNull(authorizations);
        this.accounts = Objects.requireNonNull(accounts);
        this.config = Objects.requireNonNull(config);
    }

    /** 先检查平台和当前账号，再把授权码重用和刷新轮换交由框架 provider 处理。 */
    @Override
    public Authentication authenticate(Authentication authentication) {
        if (!"true".equalsIgnoreCase(config.getPlainValue(ConfigConstants.MCP_ENABLED))) {
            throw new OAuth2AuthenticationException(new OAuth2Error("temporarily_unavailable"));
        }
        String token;
        OAuth2TokenType type;
        if (authentication instanceof OAuth2AuthorizationCodeAuthenticationToken code) {
            token = code.getCode();
            type = new OAuth2TokenType("code");
        } else if (authentication instanceof OAuth2RefreshTokenAuthenticationToken refresh) {
            token = refresh.getRefreshToken();
            type = OAuth2TokenType.REFRESH_TOKEN;
        } else {
            return null;
        }
        if (!(authentication.getPrincipal() instanceof OAuth2ClientAuthenticationToken client)
                || !client.isAuthenticated() || client.getRegisteredClient() == null) {
            throw new OAuth2AuthenticationException(new OAuth2Error("invalid_client"));
        }
        var stored = authorizations instanceof McpClientAuthorizationLookup scoped
                ? scoped.findByTokenForClient(token, type, client.getRegisteredClient().getId())
                : authorizations.findByToken(token, type);
        if (stored == null) {
            throw new OAuth2AuthenticationException(new OAuth2Error("invalid_grant"));
        }
        Long auditUser = null;
        try { auditUser = Long.valueOf(stored.getPrincipalName()); }
        catch (NumberFormatException exception) { /* 非规范主体不参与账号归属。 */ }
        McpAuditContext.verified(auditUser, client.getRegisteredClient().getId(), stored.getAttribute(McpOAuthAttributes.GRANT_ID));
        try {
            long id = Long.parseLong(stored.getPrincipalName());
            var current = accounts.loadActiveUser(id);
            if (id <= 0 || current == null || current.getUser() == null || !Long.valueOf(id).equals(current.getUserId())) {
                throw new AccessDeniedException("账号不可用");
            }
        } catch (NumberFormatException | AccessDeniedException exception) {
            throw new OAuth2AuthenticationException(new OAuth2Error("invalid_grant"));
        }
        return delegate.authenticate(authentication);
    }

    /** 只支持被装饰框架 provider 的原认证类型。 */
    @Override
    public boolean supports(Class<?> authentication) {
        return delegate.supports(authentication);
    }
}
