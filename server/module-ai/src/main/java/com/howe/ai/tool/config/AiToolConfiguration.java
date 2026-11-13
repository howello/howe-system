package com.howe.ai.tool.config;

import com.howe.ai.service.IAiCallLogService;
import com.howe.ai.service.IAiModelService;
import com.howe.ai.tool.provider.AiReadToolProvider;
import com.howe.common.tool.spi.BusinessToolProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 安装 MCP 时装配 AI 只读工具。
 *
 * <p>未安装时不创建 Provider，也不查询任何 MCP 表；读取 {@code howe.mcp.installed}
 * 配置键不需要引用 MCP Java 类型。</p>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "howe.mcp", name = "installed", havingValue = "true")
public class AiToolConfiguration {

    /**
     * 注册 AI 只读 Provider。
     *
     * @param models 模型服务
     * @param callLogs 调用记录服务
     * @return 工具提供者
     */
    @Bean
    public BusinessToolProvider mcpAiReadTools(IAiModelService models, IAiCallLogService callLogs) {
        return new AiReadToolProvider(models, callLogs);
    }
}
