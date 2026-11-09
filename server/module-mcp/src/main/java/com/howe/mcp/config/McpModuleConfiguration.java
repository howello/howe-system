package com.howe.mcp.config;

import com.howe.mcp.service.impl.McpAuthorizationCodecImpl;
import com.howe.mcp.service.impl.McpCredentialCleanupServiceImpl;
import com.howe.mcp.service.impl.McpGrantServiceImpl;
import com.howe.mcp.service.impl.McpJdbcAuthorizationServiceImpl;
import com.howe.mcp.service.impl.McpManagementServiceImpl;
import com.howe.mcp.service.impl.McpSelfServiceImpl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.howe.common.config.ConfigProvider;
import com.howe.common.constant.ConfigConstants;
import com.howe.common.utils.crypto.SecretCipher;
import com.howe.mcp.service.security.McpExecutionIdentity;
import com.howe.mcp.service.McpAuthorizationCodec;
import com.howe.mcp.service.McpAuditService;
import com.howe.mcp.service.McpGrantService;
import com.howe.mcp.service.McpJdbcAuthorizationService;
import com.howe.mcp.service.McpSelfService;
import com.howe.mcp.service.McpManagementService;
import com.howe.mcp.service.McpCredentialCleanupService;
import com.howe.mcp.task.McpCredentialCleanupTask;
import com.howe.mcp.service.spi.McpAccountBridge;
import com.howe.mcp.tool.McpConnectionCheck;
import com.howe.mcp.tool.McpToolRegistry;
import java.time.Clock;
import java.util.List;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** MCP 显式安装装配，默认不创建存储或工具对象；不启用默认授权服务器和 SDK 自动扫描。 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@Import(McpAuditConfiguration.class)
@ConditionalOnProperty(prefix = "howe.mcp", name = "installed", havingValue = "true")
public class McpModuleConfiguration {

    /** 读取固定地址快照；只在显式允许时接受本机 HTTP 开发地址。 */
    @Bean
    public McpEndpointSettings mcpEndpointSettings(ConfigProvider config,
            @Value("${howe.mcp.allow-local-http:false}") boolean allowLocalHttp) {
        return McpEndpointSettings.load(config, allowLocalHttp);
    }

    /** 本模块独立的 JDBC 辅助对象，不替换项目全局 JdbcTemplate 或事务管理器。 */
    @Bean
    public Persistence mcpPersistence(DataSource source, PlatformTransactionManager transactionManager) {
        JdbcTemplate jdbc = new JdbcTemplate(source);
        for (String table : List.of("oauth2_registered_client", "mcp_client_ext", "oauth2_authorization_consent",
                "mcp_grant", "oauth2_authorization", "mcp_token_index", "mcp_tool_config", "mcp_call_log")) {
            // 表名为代码固定白名单，安装时缺表立即失败；未安装时不执行任何这些查询。
            jdbc.queryForList("select 1 from " + table + " where 1=0");
        }
        jdbc.queryForList("select metadata_version,metadata_valid from mcp_client_ext where 1=0");
        return new Persistence(jdbc, new TransactionTemplate(transactionManager));
    }

    /** 客户端登记沿用框架持久化实现，public client 不生成无意义的 secret。 */
    @Bean
    public JdbcRegisteredClientRepository mcpRegisteredClients(Persistence persistence) {
        return new JdbcRegisteredClientRepository(persistence.jdbc());
    }

    /** 用户同意范围只有这一份权威存储。 */
    @Bean
    public JdbcOAuth2AuthorizationConsentService mcpConsents(Persistence persistence, JdbcRegisteredClientRepository clients) {
        return new JdbcOAuth2AuthorizationConsentService(persistence.jdbc(), clients);
    }

    /** 显式安装时验证主密钥可用，不允许首次请求时再退化成明文保存。 */
    @Bean
    public McpAuthorizationCodec mcpAuthorizationCodec(SecretCipher cipher, JdbcRegisteredClientRepository clients) {
        cipher.encrypt("mcp-key-configuration-check");
        return new McpAuthorizationCodecImpl(cipher, clients);
    }

    /** 授权与令牌索引采用已经验证的同一数据源事务。 */
    @Bean
    public McpJdbcAuthorizationService mcpAuthorizations(Persistence persistence, McpAuthorizationCodec codec,
            JdbcOAuth2AuthorizationConsentService consents) {
        return new McpJdbcAuthorizationServiceImpl(persistence.jdbc(), persistence.transactions(), codec, consents, Clock.systemUTC());
    }

    /** 当前授权守卫与用户所有权撤销服务，不缓存有效授权。 */
    @Bean
    public McpGrantService mcpGrants(Persistence persistence, JdbcOAuth2AuthorizationConsentService consents,
            ConfigProvider config, McpEndpointSettings endpoints) {
        return new McpGrantServiceImpl(persistence.jdbc(), persistence.transactions(), consents, config, endpoints);
    }

    /** 仅以本人账号主键查询自助连接和调用元数据。 */
    @Bean
    public McpSelfService mcpSelfService(Persistence persistence, JdbcOAuth2AuthorizationConsentService consents) {
        return new McpSelfServiceImpl(persistence.jdbc(), consents);
    }

    /** 后台管理复用相同存储和撤销事务，不创建另一个授权源。 */
    @Bean
    public McpManagementService mcpManagementService(Persistence persistence, JdbcRegisteredClientRepository clients,
            McpGrantService grants, McpToolRegistry tools) {
        return new McpManagementServiceImpl(persistence.jdbc(), persistence.transactions(), clients, grants, tools);
    }

    /** 清理事务使用当前提交视图和独立超时，避免候选读取快照覆盖并发轮换结果。 */
    @Bean
    public McpCredentialCleanupService mcpCredentialCleanup(DataSource source, PlatformTransactionManager manager, ConfigProvider config) {
        var jdbc = new JdbcTemplate(source);
        jdbc.setQueryTimeout(2);
        var transactions = new TransactionTemplate(manager);
        transactions.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);
        transactions.setTimeout(5);
        return new McpCredentialCleanupServiceImpl(jdbc, transactions, config, Clock.systemUTC());
    }

    /** 显式安装才注册定时任务，运行开关关闭不阻止过期清理。 */
    @Bean
    public McpCredentialCleanupTask mcpCredentialCleanupTask(McpCredentialCleanupService service) {
        return new McpCredentialCleanupTask(service);
    }

    /** 每次实际工具调用按 ID 加载当前正常账号。 */
    @Bean
    public McpExecutionIdentity mcpExecutionIdentity(McpAccountBridge accounts) {
        return new McpExecutionIdentity(accounts);
    }

    /** 只装配诊断工具；状态检查接真实数据库服务，关闭时不使用测试守卫。 */
    @Bean(destroyMethod = "close")
    public McpToolRegistry mcpTools(McpExecutionIdentity identity, McpGrantService grants, ConfigProvider config,
            ObjectMapper mapper, McpAuditService audit) {
        String configured = config.getPlainValue(ConfigConstants.MCP_TOOL_CONCURRENCY);
        int concurrency = configured == null || configured.isBlank() ? 8 : Integer.parseInt(configured);
        String configuredUser = config.getPlainValue(ConfigConstants.MCP_TOOL_USER_CONCURRENCY);
        int userConcurrency = configuredUser == null || configuredUser.isBlank() ? 2 : Integer.parseInt(configuredUser);
        return new McpToolRegistry(List.of(McpConnectionCheck.definition()), identity, grants::requireActive,
                grants::toolEnabled, mapper, concurrency, userConcurrency, audit::tool);
    }

    /** 仅供本模块装配使用的辅助组件，不向全局发布另一个事务管理器。 */
    public record Persistence(JdbcOperations jdbc, TransactionTemplate transactions) { }
}
