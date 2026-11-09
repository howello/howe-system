package com.howe.mcp.service.impl;

import com.howe.mcp.service.McpCimdClientService;
import com.howe.mcp.service.McpClientRegistrationService;

import cn.hutool.core.util.IdUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.howe.common.config.ConfigProvider;
import com.howe.common.constant.ConfigConstants;
import com.howe.mcp.config.McpEndpointSettings;
import com.howe.mcp.dto.McpClientMetadata;
import com.howe.mcp.service.security.McpAuditContext;
import com.howe.mcp.service.security.McpMetadataException;
import com.howe.mcp.service.security.McpMetadataFetcher;
import com.howe.mcp.service.security.McpTokenHash;
import com.howe.mcp.service.security.McpUriPolicy;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** CIMD 的稳定登记、持久化缓存和变更失效；网络不进入数据库事务，迟到结果不能覆盖新版本。 */
@Slf4j
public final class McpCimdClientServiceImpl implements McpCimdClientService {
    private final JdbcOperations jdbc;
    private final TransactionTemplate transactions;
    private final RegisteredClientRepository clients;
    private final ConfigProvider config;
    private final McpEndpointSettings endpoints;
    private final Source source;
    private final Consumer<String> admission;
    private final ObjectMapper canonical;
    private final Clock clock;

    /** 与现有 JDBC 客户端和授权使用同一数据源；来源配额由登记入口共享 Redis 执行。 */
    public McpCimdClientServiceImpl(JdbcOperations jdbc, TransactionTemplate transactions, RegisteredClientRepository clients,
            ConfigProvider config, McpEndpointSettings endpoints, Source source, Consumer<String> admission,
            ObjectMapper mapper, Clock clock) {
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(Objects.requireNonNull(transactions).getTransactionManager(), transactions);
        this.transactions.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        this.clients = clients; this.config = config; this.endpoints = endpoints;
        this.source = source; this.admission = admission; this.clock = clock;
        canonical = mapper.copy().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    }

    /** 新登记不会创建用户授权，缓存命中也始终检查管理员停用状态。 */
    @Override
    public RegisteredClient ensure(String clientId, String remoteIp) {
        if (!McpCimdClientService.isCandidate(clientId)) { return null; }
        if (clientId.length() > 2048 || TransactionSynchronizationManager.isActualTransactionActive()) { throw new McpMetadataException(true); }
        if (!"true".equalsIgnoreCase(config.getPlainValue(ConfigConstants.MCP_ENABLED))) { throw new McpMetadataException(true); }
        State expected = state(clientId, false);
        if (expected != null) {
            if (!"0".equals(expected.status())) { throw new McpMetadataException(false); }
            if (!"CIMD".equals(expected.source()) || fresh(expected)) { return clients.findById(expected.id()); }
        } else if (clients.findByClientId(clientId) != null) { throw new McpMetadataException(true); }
        try {
            McpUriPolicy.requireMetadataUri(clientId);
            if (clientId.equals(endpoints.issuer()) || clientId.equals(endpoints.resource())) { throw new IllegalArgumentException(); }
        } catch (IllegalArgumentException exception) { throw new McpMetadataException(false); }
        admission.accept(remoteIp);
        Instant started = clock.instant();
        McpClientMetadata metadata;
        McpMetadataFetcher.Document fetched;
        try {
            fetched = source.fetch(clientId);
            metadata = McpClientMetadata.parseCimd(clientId, fetched.json());
        } catch (McpMetadataException exception) {
            if (!exception.isTransientFailure() && expected != null) { invalidateRejected(clientId, expected); }
            throw exception;
        } catch (com.howe.mcp.service.security.McpRegistrationException exception) {
            if (expected != null) { invalidateRejected(clientId, expected); }
            throw new McpMetadataException(false);
        }
        String snapshot = snapshot(clientId, metadata);
        int ttl = Math.min(600, Math.max(0, fetched.cacheSeconds()));
        RegisteredClient result = transactions.execute(status -> persist(clientId, expected, metadata, snapshot, started.plusSeconds(ttl)));
        if (result == null) { throw new McpMetadataException(true); }
        McpAuditContext.verified(null, result.getId(), null);
        return result;
    }

    /** 所有客户端的授权请求和同意都持有客户端行锁，与元数据更新、管理员停用串行。 */
    @Override
    public <T> T withClientLock(String clientId, Supplier<T> action) {
        return transactions.execute(status -> {
            lockForAuthorization(clientId);
            T result = action.get();
            // 合法初始请求即使正在等待浏览器登录，也不再视为长期无人使用的登记。
            jdbc.update("""
                    update mcp_client_ext set last_used_at=current_timestamp
                     where registered_client_id in(select id from oauth2_registered_client where client_id=?)
                    """, clientId);
            return result;
        });
    }

    /** 用户确认从自身事务调用同一行锁，不嵌套第二个可能改变拒绝提交语义的事务边界。 */
    @Override
    public void lockForAuthorization(String clientId) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) { throw new IllegalStateException("客户端锁需要授权事务"); }
        State current = state(clientId, true);
        if (current == null || !"0".equals(current.status()) || "CIMD".equals(current.source()) && !"Y".equals(current.valid())) { throw new McpMetadataException(false); }
    }

    /** 使用数据库版本比较网络开始前的快照；不在任何行锁中等待 DNS 或 HTTP。 */
    private RegisteredClient persist(String clientId, State expected, McpClientMetadata metadata, String snapshot, Instant expires) {
        int maximum = lockQuota();
        State current = state(clientId, true);
        if (current != null && !"0".equals(current.status())) { throw new McpMetadataException(false); }
        if (current != null && !"CIMD".equals(current.source())) { return clients.findById(current.id()); }
        if (expected == null && current != null || expected != null && (current == null || expected.version() != current.version())) {
            if (current != null && fresh(current)) { return clients.findById(current.id()); }
            throw new McpMetadataException(true);
        }
        RegisteredClient previous = current == null ? null : clients.findById(current.id());
        if (current == null) {
            Long count = jdbc.queryForObject("select count(*) from mcp_client_ext", Long.class);
            if (count == null || count >= maximum) { throw new McpMetadataException(true); }
        } else if (!McpTokenHash.of(snapshot).equals(current.digest())) { invalidate(current.id()); }
        RegisteredClient registered = registered(clientId, metadata, previous);
        clients.save(registered);
        if (current == null) {
            jdbc.update("""
                    insert into mcp_client_ext(registered_client_id,registration_source,metadata_uri,metadata_digest,
                                              metadata_json,metadata_expires_at,metadata_version,metadata_valid)
                    values(?,'CIMD',?,?,?,?,1,'Y')
                    """, registered.getId(), clientId, McpTokenHash.of(snapshot), snapshot, Timestamp.from(expires));
        } else {
            jdbc.update("""
                    update mcp_client_ext set metadata_uri=?,metadata_digest=?,metadata_json=?,metadata_expires_at=?,
                           metadata_version=metadata_version+1,metadata_valid='Y',update_time=current_timestamp
                     where registered_client_id=?
                    """, clientId, McpTokenHash.of(snapshot), snapshot, Timestamp.from(expires), registered.getId());
        }
        return registered;
    }

    /** 只将同一版本的确定拒绝转为失效，不让迟到失败撤销另一个请求已重新验证的版本。 */
    private void invalidateRejected(String clientId, State expected) {
        transactions.executeWithoutResult(status -> {
            State current = state(clientId, true);
            if (current == null || current.version() != expected.version() || !"CIMD".equals(current.source())) { return; }
            invalidate(current.id());
            jdbc.update("""
                    update mcp_client_ext set metadata_valid='N',metadata_expires_at=null,
                           metadata_version=metadata_version+1,update_time=current_timestamp where registered_client_id=?
                    """, current.id());
        });
    }

    /** 固定顺序锁定 grant、授权载荷和凭据；连待确认 state 一起失效，载荷继续加密保存。 */
    private void invalidate(String registeredClientId) {
        McpClientLifecycle.invalidate(jdbc, registeredClientId);
    }

    /** DCR/CIMD 创建共用配额互斥行；更新也短暂串行，管理员调限额不会产生双创建。 */
    private int lockQuota() {
        List<String> rows = jdbc.query("select config_value from sys_config where config_key=? for update",
                (row, index) -> row.getString(1), ConfigConstants.MCP_REGISTRATION_MAX_CLIENTS);
        if (rows.size() != 1) { throw new McpMetadataException(true); }
        return McpClientRegistrationService.parseLimit(rows.get(0), 1, 100000);
    }

    /** 固定顺序的白名单 JSON 使摘要不受 JVM Map 迭代顺序或远端无关字段影响。 */
    private String snapshot(String clientId, McpClientMetadata metadata) {
        var values = new TreeMap<String, Object>(metadata.publicValues());
        values.put("client_id", clientId);
        try { return canonical.writeValueAsString(values); }
        catch (JsonProcessingException exception) { throw new McpMetadataException(true); }
    }

    /** 稳定服务端登记主键与 URL 身份，设置仅采用已实现的公共认证和轮换策略。 */
    private RegisteredClient registered(String clientId, McpClientMetadata metadata, RegisteredClient previous) {
        var builder = RegisteredClient.withId(previous == null ? IdUtil.fastSimpleUUID() : previous.getId())
                .clientId(clientId).clientIdIssuedAt(previous == null ? clock.instant() : previous.getClientIdIssuedAt())
                .clientName(metadata.name()).clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .clientSettings(ClientSettings.builder().requireProofKey(true).requireAuthorizationConsent(true).build())
                .tokenSettings(TokenSettings.builder().authorizationCodeTimeToLive(Duration.ofMinutes(2))
                        .accessTokenTimeToLive(Duration.ofMinutes(15)).accessTokenFormat(OAuth2TokenFormat.REFERENCE)
                        .refreshTokenTimeToLive(Duration.ofDays(30)).reuseRefreshTokens(false).build());
        metadata.redirects().forEach(builder::redirectUri);
        metadata.scopes().forEach(builder::scope);
        metadata.grants().forEach(value -> builder.authorizationGrantType(new AuthorizationGrantType(value)));
        return builder.build();
    }

    /** 缓存命中不读取原始文档，更不会将旧文档作为网络故障后的自动降级结果。 */
    private boolean fresh(State value) {
        return "Y".equals(value.valid()) && value.expires() != null && value.expires().isAfter(clock.instant());
    }

    /** 行锁只在调用方短事务中使用，不在元数据获取过程中持有。 */
    private State state(String clientId, boolean lock) {
        String statement = "select e.* from oauth2_registered_client c join mcp_client_ext e on c.id=e.registered_client_id where c.client_id=?";
        String parameter = clientId;
        if (lock) {
            // 显式锁定 registered_client→ext，不能让 JOIN 执行计划改变与登记删除的锁顺序。
            List<String> identifiers = jdbc.query("select id from oauth2_registered_client where client_id=? for update",
                    (row, index) -> row.getString(1), clientId);
            if (identifiers.isEmpty()) { return null; }
            parameter = identifiers.get(0);
            statement = "select * from mcp_client_ext where registered_client_id=? for update";
        }
        List<State> rows = jdbc.query(statement, (row, index) -> {
                    Timestamp expiry = row.getTimestamp("metadata_expires_at");
                    return new State(row.getString("registered_client_id"), row.getString("registration_source"), row.getString("status"),
                            row.getLong("metadata_version"), row.getString("metadata_valid"), row.getString("metadata_digest"),
                            expiry == null ? null : expiry.toInstant());
                }, parameter);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** 数据库缓存快照，不包含 secret 或用户授权。 */
    private record State(String id, String source, String status, long version, String valid, String digest, Instant expires) { }
}
