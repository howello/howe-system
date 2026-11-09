package com.howe.mcp.service.impl;

import com.howe.mcp.service.McpBearerAuthenticationService;
import com.howe.mcp.service.security.McpBearerIdentity;
import com.howe.mcp.service.security.McpInvocationContext;
import com.howe.mcp.service.security.McpOAuthAttributes;
import com.howe.mcp.service.security.McpScopes;
import com.howe.mcp.service.security.McpAuditContext;
import com.howe.mcp.service.security.McpTokenHash;

import com.howe.common.config.ConfigProvider;
import com.howe.common.constant.ConfigConstants;
import com.howe.mcp.config.McpEndpointSettings;
import com.howe.mcp.service.McpGrantService;
import com.howe.mcp.service.McpJdbcAuthorizationService;
import com.howe.mcp.service.McpCimdClientService;
import com.howe.mcp.service.spi.McpAccountBridge;
import java.time.Clock;
import java.util.Collection;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

/** 本地不透明 Bearer 认证，每次按哈希索引、当前账号及 grant 校验，不接受后台 JWT。 */
@Slf4j
public final class McpBearerAuthenticationServiceImpl implements McpBearerAuthenticationService {
    private final McpJdbcAuthorizationService authorizations;
    private final RegisteredClientRepository clients;
    private final McpGrantService grants;
    private final McpAccountBridge accounts;
    private final ConfigProvider config;
    private final McpEndpointSettings endpoints;
    private final Clock clock;
    private final McpCimdClientService cimd;

    /** 注入当前进程的可信地址和同一套令牌、账号与授权状态服务。 */
    public McpBearerAuthenticationServiceImpl(McpJdbcAuthorizationService authorizations, RegisteredClientRepository clients,
            McpGrantService grants, McpAccountBridge accounts, ConfigProvider config, McpEndpointSettings endpoints, Clock clock,
            McpCimdClientService cimd) {
        this.authorizations = Objects.requireNonNull(authorizations);
        this.clients = Objects.requireNonNull(clients);
        this.grants = Objects.requireNonNull(grants);
        this.accounts = Objects.requireNonNull(accounts);
        this.config = Objects.requireNonNull(config);
        this.endpoints = Objects.requireNonNull(endpoints);
        this.clock = Objects.requireNonNull(clock);
        this.cimd = Objects.requireNonNull(cimd);
    }

    /** access 只用于资源认证，不把 refresh/code/state 当作访问凭据。 */
    @Override
    public McpBearerIdentity authenticate(String token, String remoteIp) {
        requireEnabled();
        if (token == null || token.isBlank() || token.length() > 8192) {
            throw invalid();
        }
        var authorization = authorizations.findByToken(token, OAuth2TokenType.ACCESS_TOKEN);
        if (authorization == null || authorization.getAccessToken() == null || !authorization.getAccessToken().isActive()) {
            throw invalid();
        }
        var access = authorization.getAccessToken();
        var value = access.getToken();
        Long grantId = authorization.getAttribute(McpOAuthAttributes.GRANT_ID);
        Long version = authorization.getAttribute(McpOAuthAttributes.AUTHORIZATION_VERSION);
        String resource = authorization.getAttribute(McpOAuthAttributes.RESOURCE);
        Object audience = access.getClaims() == null ? null : access.getClaims().get("aud");
        var client = clients.findById(authorization.getRegisteredClientId());
        if (client == null || grantId == null || version == null || grantId <= 0 || version <= 0
                || !endpoints.resource().equals(resource) || !(audience instanceof Collection<?> audiences)
                || audiences.size() != 1 || !audiences.contains(resource) || value.getExpiresAt() == null
                || !value.getExpiresAt().isAfter(clock.instant()) || !value.getScopes().contains(McpScopes.CONNECT)
                || !McpScopes.SUPPORTED.containsAll(value.getScopes())
                || !authorization.getAuthorizedScopes().containsAll(value.getScopes())) {
            throw invalid();
        }
        cimd.ensure(client.getClientId(), remoteIp);
        try {
            long userId = Long.parseLong(authorization.getPrincipalName());
            var user = accounts.loadActiveUser(userId);
            if (userId <= 0 || !String.valueOf(userId).equals(authorization.getPrincipalName())
                    || user == null || user.getUser() == null || !Long.valueOf(userId).equals(user.getUserId())) {
                throw invalid();
            }
            var invocation = new McpInvocationContext(userId, user.getUsername(), user.getDeptId(), client.getClientId(),
                    grantId, version, value.getScopes(), resource, McpAuditContext.requestId());
            grants.requireActive(invocation);
            return new McpBearerIdentity(invocation, authorization.getId(), McpTokenHash.of(token), value.getExpiresAt());
        } catch (NumberFormatException | AccessDeniedException exception) {
            throw invalid();
        }
    }

    /** SSE/会话清理无需保存明文 token；重新检查哈希状态、当前账号和授权。 */
    @Override
    public boolean isCurrent(McpBearerIdentity identity) {
        try {
            requireEnabled();
            if (identity == null || !identity.expiresAt().isAfter(clock.instant())
                    || !authorizations.isAccessIndexActive(identity.tokenHash(), identity.authorizationId())) {
                return false;
            }
            var user = accounts.loadActiveUser(identity.invocation().userId());
            if (user == null || user.getUser() == null || !identity.invocation().userId().equals(user.getUserId())) {
                return false;
            }
            grants.requireActive(identity.invocation());
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    /** 平台停用与凭据失效采用不同的协议状态，均不回显内部配置。 */
    private void requireEnabled() {
        if (!"true".equalsIgnoreCase(config.getPlainValue(ConfigConstants.MCP_ENABLED))) {
            throw new OAuth2AuthenticationException(new OAuth2Error("temporarily_unavailable"));
        }
    }

    /** 认证失败不区分账号、客户端、令牌或授权是否存在。 */
    private static OAuth2AuthenticationException invalid() {
        return new OAuth2AuthenticationException(new OAuth2Error("invalid_token"));
    }
}
