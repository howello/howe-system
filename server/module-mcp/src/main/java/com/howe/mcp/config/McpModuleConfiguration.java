package com.howe.mcp.config;

import com.howe.mcp.service.impl.McpAuthorizationCodecImpl;
import com.howe.mcp.service.impl.McpCredentialCleanupServiceImpl;
import com.howe.mcp.service.impl.McpGrantServiceImpl;
import com.howe.mcp.service.impl.McpJdbcAuthorizationServiceImpl;
import com.howe.mcp.service.impl.McpManagementServiceImpl;
import com.howe.mcp.service.impl.McpSelfServiceImpl;
import com.howe.mcp.service.impl.McpToolPolicyServiceImpl;

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
import com.howe.mcp.service.McpToolPolicyService;
import com.howe.mcp.service.spi.McpToolExecutionBridge;
import com.howe.common.tool.spi.BusinessToolProvider;
import com.howe.mcp.service.security.McpToolScopeBindings;
import com.howe.mcp.operation.McpOperationBindingService;
import com.howe.mcp.operation.McpOperationDispatchTask;
import com.howe.mcp.operation.McpOperationExecutor;
import com.howe.mcp.operation.McpOperationService;
import com.howe.mcp.operation.McpOperationStore;
import com.howe.mcp.operation.McpOperationStoreImpl;
import com.howe.mcp.operation.McpToolOperationGateway;
import com.howe.mcp.tool.McpToolDefinitionAdapter;
import com.howe.mcp.task.McpCredentialCleanupTask;
import com.howe.mcp.service.spi.McpAccountBridge;
import com.howe.mcp.tool.McpConnectionCheck;
import com.howe.mcp.tool.McpToolDefinition;
import com.howe.mcp.tool.McpToolRegistry;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
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
                "mcp_grant", "oauth2_authorization", "mcp_token_index", "mcp_tool_config", "mcp_call_log",
                "mcp_operation", "mcp_operation_event", "mcp_operation_step")) {
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

    /** 目录过滤与执行复核共用同一策略实现。 */
    @Bean
    public McpToolPolicyService mcpToolPolicyService() {
        return new McpToolPolicyServiceImpl();
    }

    /** 操作账本使用同一数据源的 JDBC 与事务；密钥不可用时安装即失败。 */
    @Bean
    public McpOperationStore mcpOperationStore(Persistence persistence, SecretCipher cipher) {
        return new McpOperationStoreImpl(persistence.jdbc(), persistence.transactions(), cipher);
    }

    /** 确认入口与浏览器查询复用同一授权查询，避免复制授权判断。 */
    @Bean
    public McpOperationBindingService mcpOperationBindings(Persistence persistence, McpOperationStore store,
            McpGrantService grants) {
        return new McpOperationBindingService(persistence.jdbc(), store, grants);
    }

    /**
     * 操作服务按工具键精确绑定 Handler。
     *
     * <p>Handler 与访问要求在装配完成后由 {@link McpOperationCollector} 统一索引，
     * 避免 Provider → Gateway → OperationService → Provider 的构造期循环依赖。</p>
     */
    @Bean
    public McpOperationService mcpOperationService(McpOperationStore store, McpEndpointSettings endpoints,
            ObjectMapper mapper, Persistence persistence) {
        return new McpOperationService(store, mapper, endpoints.frontendBaseUrl().toASCIIString(),
                persistence.transactions());
    }

    /**
     * 装配完成后收集 Handler、工具访问要求与步骤版本，写入操作服务。
     *
     * @param operations 操作服务
     * @param handlers 全部操作处理器
     * @param providers 全部业务工具提供者
     * @param stepHandlers 全部后续步骤处理器
     * @return 收集器
     */
    @Bean
    public com.howe.mcp.operation.McpOperationCollector mcpOperationCollector(McpOperationService operations,
            ObjectProvider<com.howe.common.tool.operation.ToolOperationHandler> handlers,
            ObjectProvider<BusinessToolProvider> providers,
            ObjectProvider<com.howe.common.tool.operation.ToolOperationStepHandler> stepHandlers) {
        return new com.howe.mcp.operation.McpOperationCollector(operations, handlers, providers, stepHandlers);
    }

    /** 领域 Provider 使用的操作网关，只转发可信作用域内的请求。 */
    @Bean
    public com.howe.common.tool.operation.ToolOperationGateway mcpToolOperationGateway(
            McpOperationService operations, McpOperationBindingService bindings, McpToolPolicyService policy,
            ObjectProvider<McpToolExecutionBridge> bridges) {
        return new McpToolOperationGateway(operations, bindings, policy, new McpToolExecutionBridge() {
            /** 延迟取得框架权限桥接，缺少桥接时拒绝读取。 */
            @Override
            public <T> T execute(com.howe.common.tool.model.ToolAccess access, java.util.function.Supplier<T> action) {
                McpToolExecutionBridge bridge = bridges.getIfAvailable();
                if (bridge == null) {
                    throw new com.howe.common.tool.exception.ToolException("execution_denied", "权限桥接不可用");
                }
                return bridge.execute(access, action);
            }
        });
    }

    /** 后续步骤账本，与父操作同库同事务。 */
    @Bean
    public com.howe.mcp.operation.McpOperationStepStore mcpOperationStepStore(Persistence persistence,
            SecretCipher cipher) {
        return new com.howe.mcp.operation.McpOperationStepStoreImpl(persistence.jdbc(), cipher);
    }

    /** 独立执行池与派发任务；不占用同步工具名额。 */
    @Bean(destroyMethod = "close")
    public McpOperationExecutor mcpOperationExecutor(McpOperationStore store, McpOperationService operations,
            McpOperationBindingService bindings, McpExecutionIdentity identity,
            ObjectProvider<McpToolExecutionBridge> bridges, ObjectMapper mapper, ConfigProvider config,
            Persistence persistence, McpToolPolicyService policy,
            com.howe.mcp.operation.McpOperationStepStore stepStore) {
        McpToolExecutionBridge bridge = bridges.getIfAvailable();
        if (bridge == null) {
            bridge = new McpToolExecutionBridge() {
                /** 无业务领域执行时不附加权限上下文。 */
                @Override
                public <T> T execute(com.howe.common.tool.model.ToolAccess access,
                        java.util.function.Supplier<T> action) {
                    return action.get();
                }
            };
        }
        McpOperationExecutor executor = new McpOperationExecutor(store, operations, bindings, identity, bridge,
                policy, mapper, config, persistence.transactions(), stepStore);
        executor.start();
        return executor;
    }

    /** 后续步骤执行器；只执行父操作登记的固定代码键步骤。 */
    @Bean(destroyMethod = "close")
    public com.howe.mcp.operation.McpOperationStepExecutor mcpOperationStepExecutor(
            com.howe.mcp.operation.McpOperationStepStore stepStore, McpOperationStore store,
            McpOperationService operationService, McpOperationBindingService bindings,
            McpExecutionIdentity identity, ObjectProvider<McpToolExecutionBridge> bridges,
            McpToolPolicyService policy, ObjectMapper mapper, ConfigProvider config,
            com.howe.mcp.operation.McpOperationCollector collector, Persistence persistence,
            McpOperationExecutor operationExecutor) {
        McpToolExecutionBridge bridge = bridges.getIfAvailable();
        if (bridge == null) {
            bridge = new McpToolExecutionBridge() {
                /** 无业务领域执行时不附加权限上下文。 */
                @Override
                public <T> T execute(com.howe.common.tool.model.ToolAccess access,
                        java.util.function.Supplier<T> action) {
                    return action.get();
                }
            };
        }
        String configured = config.getPlainValue(ConfigConstants.MCP_OPERATION_MEAL_AUDIT_CONCURRENCY);
        int concurrency = configured == null || configured.isBlank() ? 1 : Integer.parseInt(configured);
        com.howe.mcp.operation.McpOperationStepExecutor executor =
                new com.howe.mcp.operation.McpOperationStepExecutor(stepStore, store, operationService, bindings,
                        identity, bridge, policy, collector.stepHandlerIndex(), mapper, concurrency, persistence.transactions(),
                        operationExecutor.executionLimiter());
        executor.start();
        return executor;
    }

    /** 每秒派发与定期清理，安装后才注册。 */
    @Bean
    public McpOperationDispatchTask mcpOperationDispatchTask(McpOperationExecutor executor,
            com.howe.mcp.operation.McpOperationStepExecutor stepExecutor) {
        return new McpOperationDispatchTask(executor, stepExecutor);
    }

    /** 本地操作恢复，与正常派发共用执行器。 */
    @Bean
    public com.howe.mcp.operation.McpOperationRecoveryTask mcpOperationRecoveryTask(McpOperationExecutor executor) {
        return new com.howe.mcp.operation.McpOperationRecoveryTask(executor);
    }

    /**
     * 只装配平台诊断和显式注册的业务工具；状态检查接真实数据库服务，关闭时不使用测试守卫。
     *
     * <p>业务 Provider 由各业务模块在安装条件下提供；零个 Provider 合法。
     * 存在业务 Provider 却缺少框架权限桥接时启动失败，防止迁移漏装桥接后静默失去数据权限保障。</p>
     */
    @Bean(destroyMethod = "close")
    public McpToolRegistry mcpTools(McpExecutionIdentity identity, McpGrantService grants, ConfigProvider config,
            ObjectMapper mapper, McpAuditService audit, McpToolPolicyService policy,
            ObjectProvider<BusinessToolProvider> providers, ObjectProvider<McpToolExecutionBridge> bridges,
            ObjectProvider<com.howe.common.tool.operation.ToolOperationGateway> gateways) {
        String configured = config.getPlainValue(ConfigConstants.MCP_TOOL_CONCURRENCY);
        int concurrency = configured == null || configured.isBlank() ? 8 : Integer.parseInt(configured);
        String configuredUser = config.getPlainValue(ConfigConstants.MCP_TOOL_USER_CONCURRENCY);
        int userConcurrency = configuredUser == null || configuredUser.isBlank() ? 2 : Integer.parseInt(configuredUser);
        McpToolScopeBindings.validateTable();
        List<BusinessToolProvider> businessProviders = providers.orderedStream().toList();
        McpToolScopeBindings.validate(businessProviders);
        List<McpToolDefinition> definitions = new ArrayList<>();
        businessProviders.forEach(provider -> provider.definitions().forEach(definition ->
                definitions.add(McpToolDefinitionAdapter.adapt(definition,
                        McpToolScopeBindings.resolve(definition.capabilityGroup())))));
        com.howe.common.tool.operation.ToolOperationGateway gateway = gateways.getIfAvailable();
        if (gateway != null) {
            new com.howe.mcp.operation.McpOperationGetToolProvider(gateway).definitions().forEach(definition ->
                    definitions.add(McpToolDefinitionAdapter.adapt(definition,
                            McpToolScopeBindings.resolve(definition.capabilityGroup()))));
        }
        definitions.add(McpConnectionCheck.definition(McpToolScopeBindings::resolve));
        McpToolExecutionBridge bridge = bridges.getIfAvailable();
        if (bridge == null) {
            if (!businessProviders.isEmpty()) {
                throw new IllegalStateException("存在业务工具 Provider 但缺少 MCP 执行权限桥接，拒绝装配");
            }
            bridge = new McpToolExecutionBridge() {
                /** 零业务 Provider 时平台工具不依赖请求级权限上下文，直接执行。 */
                @Override
                public <T> T execute(com.howe.common.tool.model.ToolAccess access,
                        java.util.function.Supplier<T> action) {
                    return action.get();
                }
            };
        }
        return new McpToolRegistry(definitions, identity, grants::requireActive,
                grants::toolEnabled, policy, bridge, mapper, concurrency, userConcurrency, audit::tool, gateway);
    }

    /** 仅供本模块装配使用的辅助组件，不向全局发布另一个事务管理器。 */
    public record Persistence(JdbcOperations jdbc, TransactionTemplate transactions) { }
}
