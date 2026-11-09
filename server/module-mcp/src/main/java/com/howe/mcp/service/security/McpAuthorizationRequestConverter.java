package com.howe.mcp.service.security;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationException;
import org.springframework.security.oauth2.server.authorization.web.authentication.OAuth2AuthorizationCodeRequestAuthenticationConverter;
import org.springframework.security.web.authentication.AuthenticationConverter;

/** 初始授权只接收 GET，拒绝参数污染以及当前未实现的 PAR/JAR 和响应模式。 */
@Slf4j
public final class McpAuthorizationRequestConverter implements AuthenticationConverter {
    private final AuthenticationConverter delegate = new OAuth2AuthorizationCodeRequestAuthenticationConverter();

    /** POST 留给独立的用户同意 converter，避免把同意提交当成初始授权。 */
    @Override
    public Authentication convert(HttpServletRequest request) {
        if (!"GET".equals(request.getMethod())) {
            return null;
        }
        if ((request.getQueryString() != null && request.getQueryString().length() > 16384)
                || request.getParameterMap().size() > 32) {
            throw invalid();
        }
        request.getParameterMap().forEach((name, values) -> {
            if (values == null || values.length != 1 || values[0] == null) {
                throw invalid();
            }
        });
        if (request.getParameterMap().containsKey("request")
                || request.getParameterMap().containsKey("request_uri")
                || (request.getParameterMap().containsKey("response_mode")
                        && !"query".equals(request.getParameter("response_mode")))) {
            throw invalid();
        }
        return delegate.convert(request);
    }

    /** 转换失败时不提供回调对象，错误留在本站且不回显原始请求。 */
    private static OAuth2AuthorizationCodeRequestAuthenticationException invalid() {
        return new OAuth2AuthorizationCodeRequestAuthenticationException(
                new OAuth2Error("invalid_request", "授权请求参数无效", null), null);
    }
}
