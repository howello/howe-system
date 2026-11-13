package com.howe.framework.tool.config;

import com.howe.common.tool.spi.BusinessToolProvider;
import com.howe.framework.tool.provider.SystemHealthToolProvider;
import com.howe.framework.tool.service.ISystemHealthReadService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 安装 MCP 时装配健康摘要工具。
 *
 * <p>未安装时不创建 Provider 与采样服务，也不查询任何 MCP 表；
 * 读取 {@code howe.mcp.installed} 配置键不需要引用 MCP Java 类型。</p>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "howe.mcp", name = "installed", havingValue = "true")
public class FrameworkToolConfiguration {

    /**
     * 注册健康摘要 Provider。
     *
     * @param health 健康读取服务
     * @return 工具提供者
     */
    @Bean
    public BusinessToolProvider mcpSystemHealthTools(ISystemHealthReadService health) {
        return new SystemHealthToolProvider(health);
    }
}
