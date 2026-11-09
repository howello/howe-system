package com.howe.mcp.service.security;

import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import org.springframework.security.crypto.keygen.Base64StringKeyGenerator;
import org.springframework.security.crypto.keygen.StringKeyGenerator;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;

/** 显式同意离线访问后签发刷新令牌，允许公共客户端并保持家族的绝对截止时间。 */
public final class McpRefreshTokenGenerator implements OAuth2TokenGenerator<OAuth2RefreshToken> {
    private final Clock clock;
    private final StringKeyGenerator keyGenerator = new Base64StringKeyGenerator(
            Base64.getUrlEncoder().withoutPadding(), 96);

    /** 使用系统 UTC 时钟。 */
    public McpRefreshTokenGenerator() {
        this(Clock.systemUTC());
    }

    /** 注入时钟以校验刷新截止时间。 */
    public McpRefreshTokenGenerator(Clock clock) {
        this.clock = Objects.requireNonNull(clock);
    }

    /** 仅处理授权码首次签发或刷新轮换；其它令牌和授权类型交回其它生成器。 */
    @Override
    public OAuth2RefreshToken generate(OAuth2TokenContext context) {
        if (!OAuth2TokenType.REFRESH_TOKEN.equals(context.getTokenType())
                || !context.getRegisteredClient().getAuthorizationGrantTypes()
                        .contains(AuthorizationGrantType.REFRESH_TOKEN)) {
            return null;
        }
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(context.getRegisteredClient().getTokenSettings()
                .getRefreshTokenTimeToLive());
        if (AuthorizationGrantType.AUTHORIZATION_CODE.equals(context.getAuthorizationGrantType())) {
            if (!context.getAuthorizedScopes().contains(McpScopes.OFFLINE_ACCESS)) {
                return null;
            }
        } else if (AuthorizationGrantType.REFRESH_TOKEN.equals(context.getAuthorizationGrantType())) {
            OAuth2Authorization authorization = context.getAuthorization();
            if (authorization == null || !authorization.getAuthorizedScopes().contains(McpScopes.OFFLINE_ACCESS)
                    || authorization.getRefreshToken() == null
                    || authorization.getRefreshToken().isInvalidated()) {
                return null;
            }
            Instant originalExpiry = authorization.getRefreshToken().getToken().getExpiresAt();
            if (originalExpiry == null || !originalExpiry.isAfter(issuedAt)) {
                return null;
            }
            if (originalExpiry.isBefore(expiresAt)) {
                expiresAt = originalExpiry;
            }
        } else {
            return null;
        }
        return new OAuth2RefreshToken(keyGenerator.generateKey(), issuedAt, expiresAt);
    }
}
