package com.howe.mcp.service.security;

import com.howe.mcp.config.McpEndpointSettings;
import java.util.Objects;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationGrantAuthenticationToken;

/** 授权码及刷新兑换必须显式指定当前 MCP resource，禁止缺省或改变令牌受众。 */
public final class McpResourceBoundAuthenticationProvider implements AuthenticationProvider {
    private final AuthenticationProvider delegate;
    private final String resource;

    /** 地址使用可信启动快照，不从请求 Host 生成。 */
    public McpResourceBoundAuthenticationProvider(AuthenticationProvider delegate, McpEndpointSettings endpoints) {
        this.delegate = Objects.requireNonNull(delegate);
        this.resource = Objects.requireNonNull(endpoints).resource();
    }

    /** 在消费凭据之前检查单值 resource；重复值经 converter 转成数组后同样拒绝。 */
    @Override
    public Authentication authenticate(Authentication authentication) {
        if (!(authentication instanceof OAuth2AuthorizationGrantAuthenticationToken grant)
                || !resource.equals(grant.getAdditionalParameters().get("resource"))) {
            throw new OAuth2AuthenticationException(new OAuth2Error("invalid_target", "必须指定当前 MCP 资源", null));
        }
        return delegate.authenticate(authentication);
    }

    /** 保留被装饰 provider 的认证类型契约。 */
    @Override
    public boolean supports(Class<?> authentication) {
        return delegate.supports(authentication);
    }
}
