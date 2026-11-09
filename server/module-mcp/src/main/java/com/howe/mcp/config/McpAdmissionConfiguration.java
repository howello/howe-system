package com.howe.mcp.config;

import com.howe.mcp.service.impl.McpProtocolAdmissionImpl;

import com.howe.common.config.ConfigProvider;
import com.howe.mcp.service.McpProtocolAdmission;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/** 共享协议限流只随 MCP 显式安装，不注册额外全局 RedisTemplate 或 Servlet Filter。 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "howe.mcp", name = "installed", havingValue = "true")
public class McpAdmissionConfiguration {
    /** 使用已有 Redis 连接，窗口状态在多个应用实例间共享。 */
    @Bean
    public McpProtocolAdmission mcpProtocolAdmission(RedisConnectionFactory connections, ConfigProvider config) {
        return new McpProtocolAdmissionImpl(new StringRedisTemplate(connections), config);
    }
}
