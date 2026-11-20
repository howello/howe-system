package com.howe.mcp.service.security;

import com.howe.mcp.service.McpCimdClientService;
import com.howe.mcp.service.spi.McpAccountBridge;
import com.howe.common.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationException;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationToken;

/** 初始授权与客户端停用、元数据更新共用客户端行锁，原生 provider 仍负责请求校验与签发授权码。 */
@Slf4j
@RequiredArgsConstructor
public final class McpCimdAuthorizationProvider implements AuthenticationProvider {
    private final AuthenticationProvider delegate;
    private final McpCimdClientService clients;
    private final McpAccountBridge accounts;

    /** 不做网络获取；HTTP 边界已完成元数据刷新，此处只与更新事务串行。 */
    @Override
    public Authentication authenticate(Authentication authentication) {
        String clientId;
        if (authentication instanceof OAuth2AuthorizationCodeRequestAuthenticationToken request) { clientId = request.getClientId(); }
        else { return null; }
        try {
            return clients.withClientLock(clientId, () -> {
                Authentication principal = (Authentication) authentication.getPrincipal();
                if (principal != null && principal.isAuthenticated() && !(principal instanceof AnonymousAuthenticationToken)) {
                    try {
                        long userId = Long.parseLong(principal.getName());
                        var account = accounts.loadActiveUser(userId);
                        if (userId <= 0 || account == null || !Long.valueOf(userId).equals(account.getUserId())) {
                            throw denied();
                        }
                    } catch (NumberFormatException | ServiceException | AuthenticationException | AccessDeniedException exception) {
                        throw denied();
                    }
                }
                return delegate.authenticate(authentication);
            });
        } catch (McpMetadataException exception) {
            throw new OAuth2AuthorizationCodeRequestAuthenticationException(new OAuth2Error("invalid_client"), null);
        }
    }

    /** 账号不可用时拒绝发码，且没有经过验证的回调可供跳转。 */
    private static OAuth2AuthorizationCodeRequestAuthenticationException denied() {
        return new OAuth2AuthorizationCodeRequestAuthenticationException(new OAuth2Error("access_denied"), null);
    }

    /** 仅支持被装饰原生 provider 的请求类型，不扩大授权能力。 */
    @Override
    public boolean supports(Class<?> authentication) { return delegate.supports(authentication); }

}
