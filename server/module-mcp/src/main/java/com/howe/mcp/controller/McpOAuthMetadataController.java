package com.howe.mcp.controller;

import com.howe.common.annotation.Anonymous;
import com.howe.common.annotation.RateLimiter;
import com.howe.common.enums.LimitType;
import com.howe.mcp.config.McpEndpointSettings;
import com.howe.mcp.service.security.McpScopes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** RFC 9728 资源发现；授权服务器元数据由同一可信配置的 SAS 过滤器提供。 */
@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "MCP OAuth 公开元数据")
@ConditionalOnProperty(prefix = "howe.mcp", name = "installed", havingValue = "true")
public class McpOAuthMetadataController {
    private final McpEndpointSettings endpoints;

    /** 只公开当前 MCP 资源与授权服务，不从 Host 推导或返回密钥与内部配置。 */
    @Anonymous
    @RateLimiter(count = 120, time = 60, limitType = LimitType.IP)
    @Operation(summary = "获取 MCP 受保护资源元数据")
    @GetMapping("/.well-known/oauth-protected-resource/mcp")
    public Map<String, Object> resource() {
        return Map.of("resource", endpoints.resource(), "authorization_servers", List.of(endpoints.issuer()),
                "scopes_supported", List.copyOf(McpScopes.SUPPORTED), "bearer_methods_supported", List.of("header"),
                "resource_name", "Howe MCP");
    }
}
