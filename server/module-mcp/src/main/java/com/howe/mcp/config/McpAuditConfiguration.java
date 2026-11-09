package com.howe.mcp.config;

import com.howe.mcp.service.impl.McpAuditServiceImpl;

import com.howe.mcp.service.McpAuditService;
import com.howe.common.config.ConfigProvider;
import com.howe.mcp.task.McpAuditCleanupTask;
import java.time.Clock;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 审计使用独立 JDBC 辅助对象和语句超时，不改变原应用的数据源与事务管理器。 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(prefix = "howe.mcp", name = "installed", havingValue = "true")
public class McpAuditConfiguration {
    /** 单线程、最多 128 条排队、每条 SQL 超时 2 秒；连接获取仍受现有连接池超时约束。 */
    @Bean(destroyMethod = "close")
    public McpAuditService mcpAudit(DataSource source) {
        var jdbc = new JdbcTemplate(source);
        jdbc.setQueryTimeout(2);
        return new McpAuditServiceImpl(jdbc, Clock.systemUTC(), 128);
    }

    /** 仅在显式安装时注册保留期任务，语句超时不代替连接池获取连接的超时。 */
    @Bean
    public McpAuditCleanupTask mcpAuditCleanup(DataSource source, ConfigProvider config) {
        var jdbc = new JdbcTemplate(source);
        jdbc.setQueryTimeout(2);
        return new McpAuditCleanupTask(jdbc, config, Clock.systemUTC());
    }
}
