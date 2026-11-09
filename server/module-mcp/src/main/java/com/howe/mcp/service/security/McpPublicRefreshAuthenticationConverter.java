package com.howe.mcp.service.security;

import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationConverter;
import org.springframework.util.StringUtils;

/** 将无客户端密钥的刷新表单转换为公共客户端认证请求，供专用 provider 验证。 */
public final class McpPublicRefreshAuthenticationConverter implements AuthenticationConverter {

    /** 只接管公共客户端的 POST 刷新请求；已有密钥或断言的认证方式继续交给框架。 */
    @Override
    public Authentication convert(HttpServletRequest request) {
        if (!"POST".equals(request.getMethod())
                || !AuthorizationGrantType.REFRESH_TOKEN.getValue()
                        .equals(request.getParameter(OAuth2ParameterNames.GRANT_TYPE))
                || request.getHeader(HttpHeaders.AUTHORIZATION) != null
                || request.getParameterMap().containsKey(OAuth2ParameterNames.CLIENT_SECRET)
                || request.getParameterMap().containsKey("client_assertion")) {
            return null;
        }
        if (request.getQueryString() != null || !isForm(request.getContentType())) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_REQUEST);
        }
        Map<String, Object> parameters = new HashMap<>();
        request.getParameterMap().forEach((name, values) -> {
            if (values.length != 1) {
                throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_REQUEST);
            }
            parameters.put(name, values[0]);
        });
        String clientId = required(parameters, OAuth2ParameterNames.CLIENT_ID);
        required(parameters, OAuth2ParameterNames.REFRESH_TOKEN);
        parameters.remove(OAuth2ParameterNames.CLIENT_ID);
        return new OAuth2ClientAuthenticationToken(clientId, ClientAuthenticationMethod.NONE, null, parameters);
    }

    /** 读取单值必填参数，不在错误信息中回显任何凭据。 */
    private static String required(Map<String, Object> parameters, String name) {
        if (!(parameters.get(name) instanceof String value) || !StringUtils.hasText(value)) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_REQUEST);
        }
        return value;
    }

    /** 仅接受标准表单媒体类型，格式错误时返回 OAuth 参数错误。 */
    private static boolean isForm(String contentType) {
        try {
            if (contentType == null) {
                return false;
            }
            MediaType actual = MediaType.parseMediaType(contentType);
            return MediaType.APPLICATION_FORM_URLENCODED.getType().equals(actual.getType())
                    && MediaType.APPLICATION_FORM_URLENCODED.getSubtype().equals(actual.getSubtype());
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
