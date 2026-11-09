package com.howe.mcp.service.impl;

import com.howe.mcp.service.McpGrantService;

import com.howe.common.config.ConfigProvider;
import com.howe.common.constant.ConfigConstants;
import com.howe.mcp.config.McpEndpointSettings;
import com.howe.mcp.service.security.McpInvocationContext;
import com.howe.mcp.service.security.McpScopes;
import com.howe.mcp.service.security.McpAuditContext;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.transaction.support.TransactionTemplate;

/** 以数据库授权及同意记录为权威的调用守卫与用户撤销服务，不缓存有效状态。 */
@Slf4j
public final class McpGrantServiceImpl implements McpGrantService {
    private static final String SELECT_GRANT = """
            select g.grant_id, g.user_id, g.registered_client_id, g.resource_uri,
                   g.status, g.authorization_version, c.client_id, e.status client_status
              from mcp_grant g join oauth2_registered_client c on c.id=g.registered_client_id
              join mcp_client_ext e on e.registered_client_id=c.id
             where g.grant_id=? and g.user_id=?
            """;
    private final JdbcOperations jdbc;
    private final TransactionTemplate transactions;
    private final OAuth2AuthorizationConsentService consents;
    private final ConfigProvider config;
    private final McpEndpointSettings endpoints;

    /** 注入同一数据源上的事务与同意服务；地址使用当前进程固定的可信快照。 */
    public McpGrantServiceImpl(JdbcOperations jdbc, TransactionTemplate transactions,
            OAuth2AuthorizationConsentService consents, ConfigProvider config, McpEndpointSettings endpoints) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.transactions = Objects.requireNonNull(transactions);
        this.consents = Objects.requireNonNull(consents);
        this.config = Objects.requireNonNull(config);
        this.endpoints = Objects.requireNonNull(endpoints);
    }

    /** 每次调用按当前记录检查主体、客户端、资源、授权版本与用户当前同意范围。 */
    @Override
    public void requireActive(McpInvocationContext invocation) {
        if (!"true".equalsIgnoreCase(config.getPlainValue(ConfigConstants.MCP_ENABLED))
                || invocation == null || invocation.userId() == null || invocation.grantId() == null) {
            throw denied();
        }
        GrantState grant = find(invocation.grantId(), invocation.userId(), false);
        if (grant == null || !"0".equals(grant.status()) || !"0".equals(grant.clientStatus())
                || !grant.clientId().equals(invocation.clientId())
                || grant.version() != invocation.authorizationVersion()
                || !endpoints.resource().equals(grant.resource()) || !grant.resource().equals(invocation.resource())
                || !invocation.scopes().contains(McpScopes.CONNECT)) {
            throw denied();
        }
        var consent = consents.findById(grant.registeredClientId(), String.valueOf(invocation.userId()));
        if (consent == null || !consent.getScopes().containsAll(invocation.scopes())) {
            throw denied();
        }
    }

    /** 工具缺失配置时关闭；此方法不替代总开关和授权检查。 */
    @Override
    public boolean toolEnabled(String name) {
        List<String> values = jdbc.query("select enabled from mcp_tool_config where tool_name=?",
                (row, index) -> row.getString(1), name);
        return values.size() == 1 && "Y".equals(values.get(0));
    }

    /**
     * 撤销本人在客户端上的整个资源授权；与同意和凭据索引在同一事务提交。
     * 关闭运行开关时仍允许撤销。重复撤销保持幂等，不恢复或创建授权。
     */
    @Override
    public void revokeOwned(Long userId, Long grantId) {
        if (userId == null || grantId == null) {
            throw denied();
        }
        transactions.executeWithoutResult(status -> {
            GrantState grant = find(grantId, userId, true);
            if (grant == null) {
                throw denied();
            }
            McpAuditContext.verified(userId, grant.registeredClientId(), grant.id());
            jdbc.update("""
                    update mcp_grant set status='1', authorization_version=authorization_version+1,
                           revoked_at=current_timestamp, update_time=current_timestamp
                     where grant_id=? and user_id=? and status='0'
                    """, grantId, userId);
            var consent = consents.findById(grant.registeredClientId(), String.valueOf(userId));
            if (consent != null) {
                consents.remove(consent);
            }
            jdbc.update("update mcp_token_index set status='REVOKED', update_time=current_timestamp where grant_id=? and status<>'REVOKED'",
                    grantId);
        });
    }

    /** 查询本人授权，撤销时锁定授权行，统一所有权校验与状态变更。 */
    private GrantState find(Long grantId, Long userId, boolean lock) {
        if (lock) {
            List<GrantState> locked = jdbc.query("select * from mcp_grant where grant_id=? and user_id=? for update",
                    (row, index) -> new GrantState(row.getLong("grant_id"), row.getLong("user_id"), row.getString("registered_client_id"),
                    null, row.getString("resource_uri"), row.getString("status"), null, row.getLong("authorization_version")), grantId, userId);
            if (locked.isEmpty()) { return null; }
            GrantState grant = locked.get(0);
            List<GrantState> related = jdbc.query("""
                    select c.client_id,e.status from oauth2_registered_client c join mcp_client_ext e on e.registered_client_id=c.id where c.id=?
                    """, (row, index) -> new GrantState(grant.id(), grant.userId(), grant.registeredClientId(), row.getString("client_id"),
                    grant.resource(), grant.status(), row.getString("status"), grant.version()), grant.registeredClientId());
            return related.isEmpty() ? null : related.get(0);
        }
        List<GrantState> rows = jdbc.query(SELECT_GRANT,
                (row, index) -> new GrantState(row.getLong("grant_id"), row.getLong("user_id"),
                        row.getString("registered_client_id"), row.getString("client_id"), row.getString("resource_uri"),
                        row.getString("status"), row.getString("client_status"), row.getLong("authorization_version")), grantId, userId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** 不回显凭据、其它用户是否存在或内部状态。 */
    private static AccessDeniedException denied() {
        return new AccessDeniedException("MCP 授权不可用或无权访问该连接");
    }

    /** 数据库加载的当前授权状态，不保存密码或令牌。 */
    private record GrantState(Long id, Long userId, String registeredClientId, String clientId, String resource,
            String status, String clientStatus, long version) {
    }
}
