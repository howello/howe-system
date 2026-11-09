package com.howe.mcp.controller;

import com.howe.common.annotation.RateLimiter;
import com.howe.common.enums.LimitType;
import com.howe.mcp.config.McpAuthorizationNavigation;
import com.howe.mcp.config.McpEndpointSettings;
import com.howe.mcp.service.security.McpAuthorizationRequestValidator;
import com.howe.mcp.service.McpJdbcAuthorizationService;
import com.howe.mcp.service.spi.McpAccountBridge;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationContext;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;
import java.nio.charset.StandardCharsets;

/** 登录恢复与同意页上下文，前端只展示服务端已验证且属于当前用户的授权请求。 */
@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "MCP 用户授权上下文")
@ConditionalOnProperty(prefix = "howe.mcp", name = "installed", havingValue = "true")
public class McpAuthorizationContextController {
    private final McpAuthorizationNavigation navigation;
    private final McpEndpointSettings endpoints;
    private final McpJdbcAuthorizationService authorizations;
    private final JdbcRegisteredClientRepository clients;
    private final McpAccountBridge accounts;

    /** 登录后只恢复本 Session 中已缓存的 GET 请求，忽略客户端任意 returnUrl 和请求 Host。 */
    @RateLimiter(count = 30, time = 60, limitType = LimitType.IP)
    @Operation(summary = "恢复登录前的 MCP 授权请求")
    @GetMapping("/oauth2/self/resume")
    public void resume(Authentication authentication, HttpServletRequest request, HttpServletResponse response) throws IOException {
        accounts.loadActiveUser(McpBrowserController.userId(authentication));
        var saved = navigation.requests().getRequest(request, response);
        String target = endpoints.connectionsPage();
        if (saved != null && "GET".equals(saved.getMethod())) {
            var url = UriComponentsBuilder.fromUriString(endpoints.issuer() + "/oauth2/authorize");
            saved.getParameterMap().forEach((name, values) -> url.queryParam(UriUtils.encode(name, StandardCharsets.UTF_8),
                    java.util.Arrays.stream(values).map(value -> UriUtils.encode(value, StandardCharsets.UTF_8)).toArray(Object[]::new)));
            target = url.build(true).toUriString();
        }
        navigation.requests().removeRequest(request, response);
        response.setHeader("Cache-Control", "no-store");
        response.sendRedirect(target);
    }

    /** state 仅用于定位本人待确认请求，客户端名称、回调与范围均从加密服务端记录加载。 */
    @RateLimiter(count = 60, time = 60, limitType = LimitType.IP)
    @Operation(summary = "获取本人 MCP 授权确认上下文")
    @GetMapping("/oauth2/self/consent-context")
    public ResponseEntity<?> consent(Authentication authentication, @RequestParam String state) {
        Long userId = McpBrowserController.userId(authentication);
        accounts.loadActiveUser(userId);
        if (state.isBlank() || state.length() > 8192) {
            return invalid();
        }
        var pending = authorizations.findByToken(state, new OAuth2TokenType("state"));
        if (pending == null || !String.valueOf(userId).equals(pending.getPrincipalName())) {
            return invalid();
        }
        var client = clients.findById(pending.getRegisteredClientId());
        OAuth2AuthorizationRequest original = pending.getAttribute(OAuth2AuthorizationRequest.class.getName());
        if (client == null || original == null) {
            return invalid();
        }
        var verified = new OAuth2AuthorizationCodeRequestAuthenticationToken(original.getAuthorizationUri(),
                client.getClientId(), authentication, original.getRedirectUri(), original.getState(),
                original.getScopes(), original.getAdditionalParameters());
        try {
            new McpAuthorizationRequestValidator(endpoints).accept(OAuth2AuthorizationCodeRequestAuthenticationContext
                    .with(verified).registeredClient(client).build());
        } catch (OAuth2AuthenticationException exception) {
            return invalid();
        }
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(new ConsentView(state, client.getClientId(),
                client.getClientName(), original.getRedirectUri(), List.copyOf(original.getScopes()), endpoints.issuer() + "/oauth2/authorize"));
    }

    /** 无效或他人的 state 使用同一响应，不泄露请求是否存在。 */
    private static ResponseEntity<Map<String, String>> invalid() {
        return ResponseEntity.badRequest().header("Cache-Control", "no-store").body(Map.of("error", "invalid_request"));
    }

    /** 授权页面所需的最小公开信息，不包含用户凭据、授权码或客户端密钥。 */
    public record ConsentView(String state, String clientId, String clientName, String redirectUri,
            List<String> scopes, String submitUrl) { }
}
