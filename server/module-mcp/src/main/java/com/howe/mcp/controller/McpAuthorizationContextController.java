package com.howe.mcp.controller;

import com.howe.common.annotation.RateLimiter;
import com.howe.common.enums.LimitType;
import com.howe.mcp.config.McpAuthorizationNavigation;
import com.howe.mcp.config.McpEndpointSettings;
import com.howe.mcp.service.spi.McpAccountBridge;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
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

}
