package com.howe.mcp.config;

import com.howe.mcp.service.impl.McpCimdClientServiceImpl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.howe.common.config.ConfigProvider;
import com.howe.mcp.service.security.McpMetadataFetcher;
import com.howe.mcp.service.McpCimdClientService;
import com.howe.mcp.service.McpRegistrationAdmission;
import java.time.Clock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;

/** 仅显式安装时装配 CIMD 获取器，复用客户端配额、固定地址快照与持久化存储。 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "howe.mcp", name = "installed", havingValue = "true")
public class McpCimdConfiguration {
    /** 获取线程和套接字随模块关闭，不使用全局 Cookie 或 TLS 配置。 */
    @Bean(destroyMethod = "close")
    public McpMetadataFetcher mcpMetadataFetcher(ObjectMapper mapper) { return new McpMetadataFetcher(mapper); }

    /** 网络获取和数据库提交为两个明确阶段，配额客户端使用与 DCR 相同的共享计数。 */
    @Bean
    public McpCimdClientService mcpCimdClients(McpModuleConfiguration.Persistence persistence,
            JdbcRegisteredClientRepository clients, ConfigProvider config, McpEndpointSettings endpoints,
            McpMetadataFetcher fetcher, McpRegistrationAdmission admission, ObjectMapper mapper) {
        return new McpCimdClientServiceImpl(persistence.jdbc(), persistence.transactions(), clients, config, endpoints,
                fetcher::fetch, admission::acquire, mapper, Clock.systemUTC());
    }
}
