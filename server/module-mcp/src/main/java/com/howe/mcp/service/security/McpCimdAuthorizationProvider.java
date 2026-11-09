package com.howe.mcp.service.security;

import com.howe.mcp.service.McpCimdClientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationException;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationToken;

/** 初始授权与客户端停用、元数据更新共用客户端行锁，原生 provider 仍负责请求校验与待确认状态创建。 */
@Slf4j
@RequiredArgsConstructor
public final class McpCimdAuthorizationProvider implements AuthenticationProvider {
    private final AuthenticationProvider delegate;
    private final McpCimdClientService clients;

    /** 不做网络获取；HTTP 边界已完成元数据刷新，此处只与更新事务串行。 */
    @Override
    public Authentication authenticate(Authentication authentication) {
        String clientId;
        if (authentication instanceof OAuth2AuthorizationCodeRequestAuthenticationToken request) { clientId = request.getClientId(); }
        else { return null; }
        try {
            return clients.withClientLock(clientId, () -> delegate.authenticate(authentication));
        } catch (McpMetadataException exception) {
            throw new OAuth2AuthorizationCodeRequestAuthenticationException(new OAuth2Error("invalid_client"), null);
        }
    }

    /** 仅支持被装饰原生 provider 的请求类型，不扩大授权能力。 */
    @Override
    public boolean supports(Class<?> authentication) { return delegate.supports(authentication); }

}
