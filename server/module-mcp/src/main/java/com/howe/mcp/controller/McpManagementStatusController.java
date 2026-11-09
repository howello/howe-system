package com.howe.mcp.controller;

import com.howe.common.config.ConfigProvider;
import com.howe.common.constant.ConfigConstants;
import com.howe.common.core.controller.BaseController;
import com.howe.common.core.domain.AjaxResult;
import com.howe.mcp.config.McpEndpointSettings;
import com.howe.mcp.service.McpAuditService;
import com.howe.mcp.tool.McpToolRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 后台只读状态入口，明确区分在线参数、启动快照和单进程计数。 */
@Slf4j
@RestController
@RequestMapping("/mcp/manage")
@Tag(name = "MCP 运行状态")
@ConditionalOnProperty(prefix = "howe.mcp", name = "installed", havingValue = "true")
public class McpManagementStatusController extends BaseController {
    private final ConfigProvider config;
    private final McpEndpointSettings active;
    private final McpAuditService audit;
    private final McpToolRegistry tools;
    private final boolean allowLocalHttp;

    /** 沿用启动时相同的本机 HTTP 策略，不因查看状态改变任何快照或配置。 */
    public McpManagementStatusController(ConfigProvider config, McpEndpointSettings active, McpAuditService audit,
            McpToolRegistry tools, @Value("${howe.mcp.allow-local-http:false}") boolean allowLocalHttp) {
        this.config = config; this.active = active; this.audit = audit; this.tools = tools; this.allowLocalHttp = allowLocalHttp;
    }

    /** 仅读取三项白名单参数；无效地址不回显原文，防止误填密码或查询串进入管理响应。 */
    @Operation(summary = "获取 MCP 生效地址与重启提示")
    @PreAuthorize("@ss.hasPermi('mcp:client:list')")
    @GetMapping("/configuration")
    public AjaxResult configuration() {
        McpEndpointSettings configured = null;
        try {
            configured = McpEndpointSettings.from(config.getPlainValue(ConfigConstants.MCP_PUBLIC_BASE_URL),
                    config.getPlainValue(ConfigConstants.MCP_FRONTEND_BASE_URL), allowLocalHttp);
        } catch (IllegalArgumentException exception) { /* 仅返回有效性，不输出或记录原始配置值。 */ }
        return success(new ConfigurationView("true".equalsIgnoreCase(config.getPlainValue(ConfigConstants.MCP_ENABLED)),
                addresses(active), configured == null ? null : addresses(configured), configured != null,
                configured == null || !configured.equals(active)));
    }

    /** 指标不包含用户标识或请求内容；计数从当前进程启动累计，不能解释成全站集群总数。 */
    @Operation(summary = "获取 MCP 单进程审计与工具执行指标")
    @PreAuthorize("@ss.hasPermi('mcp:calllog:list')")
    @GetMapping("/statistics")
    public AjaxResult statistics() { return success(new StatisticsView(audit.statistics(), tools.statistics())); }

    /** 已校验公开站点转换成最小显示对象，不返回配置提供者或任意参数表项。 */
    private static AddressView addresses(McpEndpointSettings value) {
        return new AddressView(value.issuer(), value.resource(), value.frontendBaseUrl().toASCIIString());
    }
    /** 当前地址与已保存地址均为经同一部署规则规范化后的站点。 */
    public record AddressView(String issuer, String resource, String frontendBaseUrl) { }
    /** 在线地址修改必须重启生效，运行开关不要求重启。 */
    public record ConfigurationView(boolean enabled, AddressView active, AddressView configured, boolean configuredValid, boolean restartRequired) { }
    /** 仅包含当前进程数量，不能通过此接口清零计数或改变并发上限。 */
    public record StatisticsView(McpAuditService.Statistics audit, McpToolRegistry.ExecutionStatistics execution) { }
}
