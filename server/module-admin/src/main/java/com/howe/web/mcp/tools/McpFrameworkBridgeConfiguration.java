package com.howe.web.mcp.tools;

import com.howe.mcp.service.spi.McpToolExecutionBridge;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MCP 框架权限桥接装配。
 *
 * <p>只注册框架执行桥接；业务工具 Provider 由各领域模块在自身条件配置中注册，
 * 本类不再登记领域工具。未安装 MCP 时不创建任何桥接 Bean。</p>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "howe.mcp", name = "installed", havingValue = "true")
public class McpFrameworkBridgeConfiguration {

    /**
     * 业务权限上下文桥接。
     *
     * @return 执行桥接
     */
    @Bean
    public McpToolExecutionBridge mcpToolExecutionBridge() {
        return new McpFrameworkExecutionBridge();
    }
}
