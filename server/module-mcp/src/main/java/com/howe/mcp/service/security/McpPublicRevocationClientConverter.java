package com.howe.mcp.service.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.web.authentication.AuthenticationConverter;

/** 只在标准撤销端点识别 none 客户端，不把 client_id 当作授权码或刷新的认证凭据。 */
@lombok.extern.slf4j.Slf4j
public final class McpPublicRevocationClientConverter implements AuthenticationConverter {

    /** 密钥或断言认证继续交给框架，不允许错误机密认证降级为公共客户端。 */
    @Override
    public Authentication convert(HttpServletRequest request) {
        if (!"POST".equals(request.getMethod())
                || !"/oauth2/revoke".equals(request.getRequestURI().substring(request.getContextPath().length()))
                || request.getHeader("Authorization") != null || request.getParameterMap().containsKey("client_secret")
                || request.getParameterMap().containsKey("client_assertion")) {
            return null;
        }
        String[] values = request.getParameterValues("client_id");
        if (values == null || values.length != 1 || values[0].isBlank()) {
            throw new OAuth2AuthenticationException(new OAuth2Error("invalid_client"));
        }
        return new McpPublicRevocationClientToken(values[0]);
    }
}
