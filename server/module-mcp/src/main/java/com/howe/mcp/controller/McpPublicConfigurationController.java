package com.howe.mcp.controller;

import com.howe.common.annotation.Anonymous;
import com.howe.common.annotation.RateLimiter;
import com.howe.common.config.ConfigProvider;
import com.howe.common.constant.ConfigConstants;
import com.howe.common.enums.LimitType;
import com.howe.mcp.config.McpEndpointSettings;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 前端同源引导，只公开已生效的地址白名单和安装状态，不返回任意参数值。 */
@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "MCP 公开连接配置")
public class McpPublicConfigurationController {
    private final ObjectProvider<McpEndpointSettings> endpoints;
    private final ConfigProvider config;

    /** 未安装时不要求 MCP 参数或表；安装后公开当前快照，供前端定位后端。 */
    @Anonymous
    @RateLimiter(count = 60, time = 60, limitType = LimitType.IP)
    @Operation(summary = "获取 MCP 公开连接配置")
    @GetMapping("/mcp/public/configuration")
    public Map<String, Object> configuration() {
        McpEndpointSettings active = endpoints.getIfAvailable();
        if (active == null) {
            return Map.of("installed", false, "enabled", false);
        }
        return Map.of("installed", true, "enabled", "true".equalsIgnoreCase(config.getPlainValue(ConfigConstants.MCP_ENABLED)),
                "backendBaseUrl", active.issuer(), "resource", active.resource(), "loginPage", active.loginPage(),
                "consentPage", active.consentPage(), "connectionsPage", active.connectionsPage(),
                "confirmationPage", active.confirmationPage(), "operationsPage", active.operationsPage());
    }
}
