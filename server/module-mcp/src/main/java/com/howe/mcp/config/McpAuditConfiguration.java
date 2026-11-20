package com.howe.mcp.config;

import com.howe.mcp.service.impl.McpAuditServiceImpl;

import com.howe.mcp.service.McpAuditService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 装配 MCP 审计和定时任务。 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(prefix = "howe.mcp", name = "installed", havingValue = "true")
public class McpAuditConfiguration {
    /** 调用与授权事件通过现有应用日志输出。 */
    @Bean
    public McpAuditService mcpAudit() {
        return new McpAuditServiceImpl();
    }
}
