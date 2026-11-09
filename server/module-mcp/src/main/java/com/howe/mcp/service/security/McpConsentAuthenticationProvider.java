package com.howe.mcp.service.security;

import com.howe.common.config.ConfigProvider;
import com.howe.common.constant.ConfigConstants;
import com.howe.common.exception.ServiceException;
import com.howe.mcp.config.McpEndpointSettings;
import com.howe.mcp.service.spi.McpAccountBridge;
import com.howe.mcp.service.McpAuditService.Result;
import com.howe.mcp.service.McpCimdClientService;
import java.util.Objects;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationCode;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsent;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationContext;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationException;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationConsentAuthenticationProvider;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationConsentAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.TransactionDefinition;

/** 用户同意的完整事务边界：复核请求、保存精确范围、绑定 grant 并由原生框架签发授权码。 */
@Slf4j
public final class McpConsentAuthenticationProvider implements AuthenticationProvider {
    private final JdbcOperations jdbc;
    private final TransactionTemplate transactions;
    private final RegisteredClientRepository clients;
    private final OAuth2AuthorizationService authorizations;
    private final OAuth2AuthorizationConsentService consents;
    private final McpAccountBridge accounts;
    private final ConfigProvider config;
    private final McpEndpointSettings endpoints;
    private final McpCimdClientService cimd;

    /** 注入同一数据源上的存储和事务，当前账号由聚合层桥接加载。 */
    public McpConsentAuthenticationProvider(JdbcOperations jdbc, TransactionTemplate transactions,
            RegisteredClientRepository clients, OAuth2AuthorizationService authorizations,
            OAuth2AuthorizationConsentService consents, McpAccountBridge accounts,
            ConfigProvider config, McpEndpointSettings endpoints, McpCimdClientService cimd) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.transactions = new TransactionTemplate(Objects.requireNonNull(transactions).getTransactionManager(), transactions);
        // grant 串行化后必须读取最新 Consent，避免 MySQL 默认快照读继承并发确认前的旧范围。
        this.transactions.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        this.clients = Objects.requireNonNull(clients);
        this.authorizations = Objects.requireNonNull(authorizations);
        this.consents = Objects.requireNonNull(consents);
        this.accounts = Objects.requireNonNull(accounts);
        this.config = Objects.requireNonNull(config);
        this.endpoints = Objects.requireNonNull(endpoints);
        this.cimd = Objects.requireNonNull(cimd);
    }

    /** 拒绝只消费本次待确认请求；同意的任何签发故障均回滚整笔授权写入。 */
    @Override
    public Authentication authenticate(Authentication authentication) {
        if (authentication == null || !supports(authentication.getClass())) {
            return null;
        }
        var submitted = (OAuth2AuthorizationConsentAuthenticationToken) authentication;
        Outcome outcome = transactions.execute(status -> {
            try { cimd.lockForAuthorization(submitted.getClientId()); }
            catch (McpMetadataException exception) {
                throw failure(null, McpCimdClientService.isCandidate(submitted.getClientId()) ? "invalid_client" : "invalid_request");
            }
            return confirm(submitted);
        });
        if (outcome == null) {
            throw new IllegalStateException("用户授权事务未返回结果");
        }
        if (outcome.denied() != null) {
            McpAuditContext.result(Result.ACCESS_DENIED);
            throw outcome.denied();
        }
        McpAuditContext.result(Result.OK);
        return outcome.authentication();
    }

    /** 不接管设备授权等非本期认证类型。 */
    @Override
    public boolean supports(Class<?> authentication) {
        return OAuth2AuthorizationConsentAuthenticationToken.class.equals(authentication);
    }

    /** 在独立事务中加载并锁定当前待确认请求，复核用户、客户端及全部已验证参数。 */
    private Outcome confirm(OAuth2AuthorizationConsentAuthenticationToken submitted) {
        if (!"true".equalsIgnoreCase(config.getPlainValue(ConfigConstants.MCP_ENABLED))) {
            throw failure(null, "temporarily_unavailable");
        }
        OAuth2Authorization pending = authorizations.findByToken(submitted.getState(), new OAuth2TokenType("state"));
        Authentication principal = (Authentication) submitted.getPrincipal();
        if (pending == null || pending.getAttribute(McpOAuthAttributes.GRANT_ID) != null
                || pending.getToken(OAuth2AuthorizationCode.class) != null || pending.getAccessToken() != null
                || pending.getRefreshToken() != null
                || !principal.isAuthenticated() || principal instanceof AnonymousAuthenticationToken
                || !principal.getName().equals(pending.getPrincipalName())) {
            throw failure(null, "invalid_request");
        }
        RegisteredClient client = clients.findByClientId(submitted.getClientId());
        if (client == null || !client.getId().equals(pending.getRegisteredClientId())) {
            throw failure(null, "invalid_request");
        }
        Long userId = requireActiveUser(principal);
        McpAuditContext.verified(userId, client.getId(), null);
        OAuth2AuthorizationRequest original = pending.getAttribute(OAuth2AuthorizationRequest.class.getName());
        if (original == null || !client.getClientId().equals(original.getClientId())) {
            throw failure(null, "invalid_request");
        }
        var verified = new OAuth2AuthorizationCodeRequestAuthenticationToken(original.getAuthorizationUri(),
                original.getClientId(), principal, original.getRedirectUri(), original.getState(),
                original.getScopes(), original.getAdditionalParameters());
        new McpAuthorizationRequestValidator(endpoints).accept(OAuth2AuthorizationCodeRequestAuthenticationContext
                .with(verified).registeredClient(client).build());
        Set<String> approved = submitted.getScopes();
        if (!original.getScopes().containsAll(approved)
                || !approved.isEmpty() && !approved.contains(McpScopes.CONNECT)) {
            throw failure(verified, "invalid_scope");
        }
        if (approved.isEmpty()) {
            // 拒绝新的连接不撤销用户此前已同意的连接；只消费本次 state。
            authorizations.remove(pending);
            return new Outcome(null, failure(verified, "access_denied"));
        }
        GrantBinding binding = lockApprovedGrant(userId, client, approved);
        McpAuditContext.verified(userId, client.getId(), binding.id());
        var provider = new OAuth2AuthorizationConsentAuthenticationProvider(clients,
                new IssuingStore(binding), new ExactConsentStore());
        return new Outcome(provider.authenticate(submitted), null);
    }

    /** 只接受规范的服务端用户主键，并在签发前刷新正常账号状态。 */
    private Long requireActiveUser(Authentication principal) {
        try {
            long userId = Long.parseLong(principal.getName());
            if (userId <= 0 || !String.valueOf(userId).equals(principal.getName())) {
                throw failure(null, "access_denied");
            }
            var current = accounts.loadActiveUser(userId);
            if (current == null || current.getUser() == null || !Long.valueOf(userId).equals(current.getUserId())) {
                throw failure(null, "access_denied");
            }
            return userId;
        } catch (NumberFormatException | ServiceException | AuthenticationException | AccessDeniedException exception) {
            throw failure(null, "access_denied");
        }
    }

    /** 在 Consent 写入之前锁定 grant，避免与撤销操作出现 grant/Consent 反向加锁。 */
    private GrantBinding lockApprovedGrant(Long userId, RegisteredClient client, Set<String> approved) {
        jdbc.update("""
                insert into mcp_grant(user_id,registered_client_id,resource_uri) values(?,?,?)
                on duplicate key update grant_id=grant_id
                """, userId, client.getId(), endpoints.resource());
        GrantBinding binding = jdbc.queryForObject("""
                select grant_id,authorization_version,status from mcp_grant
                 where user_id=? and registered_client_id=? and resource_uri=? for update
                """, (row, index) -> new GrantBinding(row.getLong("grant_id"),
                row.getLong("authorization_version"), row.getString("status")), userId, client.getId(), endpoints.resource());
        if (binding == null) {
            throw new IllegalStateException("用户授权绑定不存在");
        }
        var previous = consents.findById(client.getId(), String.valueOf(userId));
        if (!"0".equals(binding.status()) || previous != null && !approved.containsAll(previous.getScopes())) {
            jdbc.update("""
                    update mcp_grant set status='0',authorization_version=authorization_version+1,
                           revoked_at=null,update_time=current_timestamp where grant_id=?
                    """, binding.id());
            jdbc.update("update mcp_token_index set status='REVOKED',update_time=current_timestamp where grant_id=?",
                    binding.id());
            return new GrantBinding(binding.id(), binding.version() + 1, "0");
        }
        return binding;
    }

    /** 构造固定协议错误，只有已校验的请求才携带可供响应处理器再次核验的回调。 */
    private static OAuth2AuthorizationCodeRequestAuthenticationException failure(
            OAuth2AuthorizationCodeRequestAuthenticationToken verified, String error) {
        return new OAuth2AuthorizationCodeRequestAuthenticationException(new OAuth2Error(error, "用户授权未完成", null), verified);
    }

    /** 本次同意只采用本次提交范围，防止框架自动合并旧 offline_access 而违背用户取消选择。 */
    private final class ExactConsentStore implements OAuth2AuthorizationConsentService {
        /** 向本次签发提供空的预批准视图，不删除数据库中仍有效的旧授权。 */
        @Override
        public OAuth2AuthorizationConsent findById(String clientId, String principalName) {
            return null;
        }

        /** 原生 provider 完成校验后，用本次精确选择替换权威 Consent。 */
        @Override
        public void save(OAuth2AuthorizationConsent consent) {
            consents.save(consent);
        }

        /** 保留框架契约；显式拒绝在进入原生 provider 前处理。 */
        @Override
        public void remove(OAuth2AuthorizationConsent consent) {
            consents.remove(consent);
        }
    }

    /** 单次确认专用的存储适配，不向全局授权服务添加自动批准行为。 */
    private final class IssuingStore implements OAuth2AuthorizationService {
        private final GrantBinding binding;

        /** 捕获本次事务已经锁定的 grant，避免共享线程上下文或全局可变状态。 */
        private IssuingStore(GrantBinding binding) {
            this.binding = binding;
        }

        /** 原生 provider 生成授权码后，在同一事务内绑定服务端 grant 和固定资源。 */
        @Override
        public void save(OAuth2Authorization authorization) {
            authorizations.save(OAuth2Authorization.from(authorization)
                    .attribute(McpOAuthAttributes.GRANT_ID, binding.id())
                    .attribute(McpOAuthAttributes.AUTHORIZATION_VERSION, binding.version())
                    .attribute(McpOAuthAttributes.RESOURCE, endpoints.resource()).build());
        }

        /** 所有读取继续经过加密授权服务与哈希索引的当前状态检查。 */
        @Override
        public OAuth2Authorization findById(String id) {
            return authorizations.findById(id);
        }

        /** 待确认 state 在同一事务持锁，重放不能创建第二个授权码。 */
        @Override
        public OAuth2Authorization findByToken(String token, OAuth2TokenType type) {
            return authorizations.findByToken(token, type);
        }

        /** 保留框架移除契约，历史凭据处理仍由原存储负责。 */
        @Override
        public void remove(OAuth2Authorization authorization) {
            authorizations.remove(authorization);
        }
    }

    /** 当前已锁定的授权版本，不含 token、code 或用户凭据。 */
    private record GrantBinding(Long id, long version, String status) { }

    /** 用户主动拒绝需要提交 state 的消费，其余签发异常按事务规则回滚。 */
    private record Outcome(Authentication authentication, OAuth2AuthorizationCodeRequestAuthenticationException denied) { }
}
