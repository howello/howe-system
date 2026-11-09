package com.howe.mcp.service.impl;

import com.howe.mcp.service.McpAuthorizationCodec;
import com.howe.mcp.service.McpJdbcAuthorizationService;

import com.howe.mcp.service.security.McpOAuthAttributes;
import com.howe.mcp.service.security.McpTokenHash;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationCode;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.transaction.support.TransactionTemplate;

/** 加密授权载荷与哈希凭据索引的 JDBC 存储；历史刷新索引保留到家族绝对过期之后。 */
@Slf4j
public final class McpJdbcAuthorizationServiceImpl implements McpJdbcAuthorizationService {
    private final JdbcOperations jdbc;
    private final TransactionTemplate transactions;
    private final McpAuthorizationCodec codec;
    private final OAuth2AuthorizationConsentService consents;
    private final Clock clock;

    /** 注入同一数据源的事务、标准 Consent 服务与加密器。 */
    public McpJdbcAuthorizationServiceImpl(JdbcOperations jdbc, TransactionTemplate transactions,
            McpAuthorizationCodec codec, OAuth2AuthorizationConsentService consents, Clock clock) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.transactions = Objects.requireNonNull(transactions);
        this.codec = Objects.requireNonNull(codec);
        this.consents = Objects.requireNonNull(consents);
        this.clock = Objects.requireNonNull(clock);
    }

    /** 在同一事务内校验绑定、保存密文和轮换索引；旧快照不得覆盖新令牌。 */
    @Override
    public void save(OAuth2Authorization authorization) {
        Objects.requireNonNull(authorization);
        transactions.executeWithoutResult(status -> {
            Long grantId = authorization.getAttribute(McpOAuthAttributes.GRANT_ID);
            GrantBinding grant = grantId == null ? null : lockGrant(grantId);
            StoredRow previous = row(authorization.getId(), true);
            if (previous != null) {
                Object version = authorization.getAttribute(McpOAuthAttributes.STORAGE_VERSION);
                if (!(version instanceof Number number) || number.longValue() != previous.version()) {
                    throw new OptimisticLockingFailureException("OAuth 授权记录已更新");
                }
                if (!previous.client().equals(authorization.getRegisteredClientId())
                        || !previous.principal().equals(authorization.getPrincipalName())
                        || previous.grantId() != null && !previous.grantId().equals(grantId)) {
                    throw new IllegalArgumentException("不能修改已有 OAuth 授权的归属");
                }
            } else if (authorization.getAttribute(McpOAuthAttributes.STORAGE_VERSION) != null) {
                throw new OptimisticLockingFailureException("OAuth 授权记录已删除");
            }
            if (authorization.getToken(OAuth2AuthorizationCode.class) != null || authorization.getAccessToken() != null
                    || authorization.getRefreshToken() != null) {
                requireBinding(authorization, grant);
            } else if (!clientActive(authorization.getRegisteredClientId())) {
                throw new IllegalStateException("OAuth 客户端不可用");
            }
            OAuth2Authorization before = previous == null ? null : decode(previous);
            preventResurrection(before, authorization);
            List<IndexedToken> tokens = tokens(authorization);
            String encrypted = codec.encode(authorization);
            Long grantVersion = authorization.getAttribute(McpOAuthAttributes.AUTHORIZATION_VERSION);
            if (previous == null) {
                jdbc.update("""
                        insert into oauth2_authorization(id,registered_client_id,principal_name,grant_id,authorization_version,encrypted_payload)
                        values(?,?,?,?,?,?)
                        """, authorization.getId(), authorization.getRegisteredClientId(), authorization.getPrincipalName(),
                        grantId, grantVersion, encrypted);
            } else {
                int updated = jdbc.update("""
                        update oauth2_authorization set grant_id=?,authorization_version=?,encrypted_payload=?,
                               row_version=row_version+1,update_time=current_timestamp where id=? and row_version=?
                        """, grantId, grantVersion, encrypted, authorization.getId(), previous.version());
                if (updated != 1) {
                    throw new OptimisticLockingFailureException("OAuth 授权记录并发冲突");
                }
            }
            syncIndexes(authorization, tokens);
        });
    }

    /** 删除载荷但保留已撤销的历史哈希，避免清理导致旧凭据重新可用。 */
    @Override
    public void remove(OAuth2Authorization authorization) {
        transactions.executeWithoutResult(status -> {
            Long grantId = authorization.getAttribute(McpOAuthAttributes.GRANT_ID);
            if (grantId != null) {
                lockGrant(grantId);
            }
            StoredRow current = row(authorization.getId(), true);
            if (current != null) {
                jdbc.update("update mcp_token_index set status='REVOKED',update_time=current_timestamp where authorization_id=?",
                        authorization.getId());
                jdbc.update("delete from oauth2_authorization where id=?", authorization.getId());
            }
        });
    }

    /** 返回带数据库版本的快照；管理查询不以此方法证明授权当前有效。 */
    @Override
    public OAuth2Authorization findById(String id) {
        StoredRow stored = row(id, false);
        return stored == null ? null : decode(stored);
    }

    /** 协议会话只保存哈希，健康检查不能把哈希误当成明文令牌再次哈希或执行重放处理。 */
    @Override
    public boolean isAccessIndexActive(String hash, String authorizationId) {
        IndexRow current = index(hash, false);
        return current != null && "access_token".equals(current.type()) && "ACTIVE".equals(current.status())
                && current.authorizationId().equals(authorizationId) && current.expiresAt().isAfter(clock.instant());
    }

    /** 只用哈希定位，并在事务内重读索引和当前授权；刷新重放会撤销对应家族。 */
    @Override
    public OAuth2Authorization findByToken(String token, OAuth2TokenType tokenType) {
        return findByToken(token, tokenType, null);
    }

    /** 客户端认证和兑换必须在重放处理之前限制授权归属；资源 Bearer 查找仍使用标准接口。 */
    @Override
    public OAuth2Authorization findByTokenForClient(String token, OAuth2TokenType tokenType, String registeredClientId) {
        if (registeredClientId == null || registeredClientId.isBlank()) {
            throw new IllegalArgumentException("客户端归属不能为空");
        }
        return findByToken(token, tokenType, registeredClientId);
    }

    /** 同时支持凭据本身认证的资源查询与带客户端归属的兑换查询。 */
    private OAuth2Authorization findByToken(String token, OAuth2TokenType tokenType, String expectedClient) {
        String hash = McpTokenHash.of(token);
        return transactions.execute(status -> {
            IndexRow initial = index(hash, false);
            if (initial == null || tokenType != null && !tokenType.getValue().equals(initial.type())) {
                return null;
            }
            if (expectedClient != null) {
                StoredRow owner = row(initial.authorizationId(), false);
                if (owner == null || !expectedClient.equals(owner.client())) {
                    return null;
                }
            }
            GrantBinding grant = initial.grantId() == null ? null : lockGrant(initial.grantId());
            StoredRow stored = row(initial.authorizationId(), true);
            IndexRow current = index(hash, true);
            if (stored == null || current == null || expectedClient != null && !expectedClient.equals(stored.client())) {
                return null;
            }
            if ("refresh_token".equals(current.type()) && "USED".equals(current.status())) {
                jdbc.update("update mcp_token_index set status='REVOKED',update_time=current_timestamp where family_id=?",
                        current.familyId());
                return null;
            }
            if ("REVOKED".equals(current.status()) || !current.expiresAt().isAfter(clock.instant())) {
                return null;
            }
            OAuth2Authorization authorization = decode(stored);
            if (current.grantId() != null) {
                if (!bindingMatches(authorization, grant)) {
                    return null;
                }
            } else if (!clientActive(stored.client())) {
                return null;
            }
            if (!matches(authorization, current.type(), token)) {
                return null;
            }
            // 已消费授权码交给框架 provider 处理重复消费与连带失效；其它旧索引不可兑换。
            if (!"ACTIVE".equals(current.status()) && !"code".equals(current.type())) {
                return null;
            }
            return authorization;
        });
    }

    /** 校验当前授权与账户、客户端、资源及 Consent 的完整绑定，不信任旧载荷范围。 */
    private boolean bindingMatches(OAuth2Authorization authorization, GrantBinding grant) {
        Long version = authorization.getAttribute(McpOAuthAttributes.AUTHORIZATION_VERSION);
        String resource = authorization.getAttribute(McpOAuthAttributes.RESOURCE);
        if (grant == null || !"0".equals(grant.status()) || !"0".equals(grant.clientStatus())
                || !grant.client().equals(authorization.getRegisteredClientId())
                || !String.valueOf(grant.userId()).equals(authorization.getPrincipalName())
                || !Objects.equals(version, grant.version()) || !Objects.equals(resource, grant.resource())) {
            return false;
        }
        var consent = consents.findById(grant.client(), authorization.getPrincipalName());
        return consent != null && consent.getScopes().containsAll(authorization.getAuthorizedScopes());
    }

    /** 签发记录必须已经由服务端授权流程绑定有效 grant；存储接口不自行代替用户同意。 */
    private void requireBinding(OAuth2Authorization authorization, GrantBinding grant) {
        if (!bindingMatches(authorization, grant)) {
            throw new IllegalStateException("OAuth 授权绑定或当前同意不可用");
        }
    }

    /** 旧刷新或旧访问令牌已经撤销时，不允许通过再次保存旧快照或轮换来复活。 */
    private void preventResurrection(OAuth2Authorization before, OAuth2Authorization after) {
        if (before == null) {
            return;
        }
        if (before.getRefreshToken() != null && after.getRefreshToken() != null && after.getRefreshToken().isActive()) {
            IndexRow previous = index(McpTokenHash.of(before.getRefreshToken().getToken().getTokenValue()), true);
            if (previous == null || !"ACTIVE".equals(previous.status())) {
                throw new OptimisticLockingFailureException("OAuth 刷新家族已失效");
            }
        }
        for (IndexedToken token : tokens(after)) {
            IndexRow previous = index(token.hash(), true);
            if (previous != null && "ACTIVE".equals(token.status()) && !"ACTIVE".equals(previous.status())) {
                throw new OptimisticLockingFailureException("OAuth 历史凭据不可恢复");
            }
        }
    }

    /** 原子更新新旧凭据索引，旧 refresh 保留 USED 标记及后继哈希。 */
    private void syncIndexes(OAuth2Authorization authorization, List<IndexedToken> tokens) {
        List<IndexRow> previous = jdbc.query("select * from mcp_token_index where authorization_id=? for update",
                (row, index) -> indexRow(row), authorization.getId());
        for (IndexRow old : previous) {
            if (tokens.stream().noneMatch(token -> token.hash().equals(old.hash())) && "ACTIVE".equals(old.status())) {
                String replacement = tokens.stream().filter(token -> token.type().equals(old.type())).map(IndexedToken::hash).findFirst().orElse(null);
                jdbc.update("update mcp_token_index set status=?,replaced_by_hash=?,update_time=current_timestamp where token_hash=?",
                        "refresh_token".equals(old.type()) || "state".equals(old.type()) ? "USED" : "REVOKED", replacement, old.hash());
            }
        }
        Long grantId = authorization.getAttribute(McpOAuthAttributes.GRANT_ID);
        Timestamp familyExpiry = authorization.getRefreshToken() == null ? null
                : Timestamp.from(authorization.getRefreshToken().getToken().getExpiresAt());
        for (IndexedToken token : tokens) {
            IndexRow existing = index(token.hash(), true);
            if (existing == null) {
                jdbc.update("""
                        insert into mcp_token_index(token_hash,token_type,authorization_id,grant_id,family_id,status,issued_at,expires_at,family_expires_at)
                        values(?,?,?,?,?,?,?,?,?)
                        """, token.hash(), token.type(), authorization.getId(), grantId, authorization.getId(), token.status(),
                        Timestamp.from(token.issuedAt()), Timestamp.from(token.expiresAt()), familyExpiry);
            } else {
                if (!existing.authorizationId().equals(authorization.getId()) || !existing.type().equals(token.type())) {
                    throw new IllegalStateException("OAuth 凭据索引冲突");
                }
                jdbc.update("update mcp_token_index set status=case when status='REVOKED' then status else ? end,grant_id=?,family_expires_at=coalesce(family_expires_at,?),update_time=current_timestamp where token_hash=?",
                        token.status(), grantId, familyExpiry, token.hash());
            }
        }
    }

    /** 提取当前凭据，明文只在栈内参与哈希，不作为 SQL 参数传入索引。 */
    private List<IndexedToken> tokens(OAuth2Authorization authorization) {
        List<IndexedToken> result = new ArrayList<>();
        String state = authorization.getAttribute(OAuth2ParameterNames.STATE);
        if (state != null) {
            result.add(new IndexedToken(McpTokenHash.of(state), "state", "ACTIVE", clock.instant(), clock.instant().plus(Duration.ofMinutes(10))));
        }
        addToken(result, "code", authorization.getToken(OAuth2AuthorizationCode.class));
        addToken(result, "access_token", authorization.getAccessToken());
        addToken(result, "refresh_token", authorization.getRefreshToken());
        return result;
    }

    /** 使用框架失效元数据确定索引状态，授权码消费与其它令牌撤销分开。 */
    private static void addToken(List<IndexedToken> result, String type, OAuth2Authorization.Token<? extends OAuth2Token> token) {
        if (token != null) {
            OAuth2Token value = token.getToken();
            if (value.getIssuedAt() == null || value.getExpiresAt() == null) {
                throw new IllegalArgumentException("OAuth 凭据必须具有发行与截止时间");
            }
            String status = token.isInvalidated() ? ("code".equals(type) ? "USED" : "REVOKED") : "ACTIVE";
            result.add(new IndexedToken(McpTokenHash.of(value.getTokenValue()), type, status, value.getIssuedAt(), value.getExpiresAt()));
        }
    }

    /** 确认哈希指向当前载荷中的实际凭据，不能把旧 token 替换成当前 token 使用。 */
    private static boolean matches(OAuth2Authorization authorization, String type, String value) {
        if ("state".equals(type)) {
            return value.equals(authorization.getAttribute(OAuth2ParameterNames.STATE));
        }
        var token = switch (type) {
            case "code" -> authorization.getToken(OAuth2AuthorizationCode.class);
            case "access_token" -> authorization.getAccessToken();
            case "refresh_token" -> authorization.getRefreshToken();
            default -> null;
        };
        return token != null && value.equals(token.getToken().getTokenValue());
    }

    /** 加载密文行；兑换事务采用 grant → authorization → token 的固定锁顺序。 */
    private StoredRow row(String id, boolean lock) {
        List<StoredRow> rows = jdbc.query("select * from oauth2_authorization where id=?" + (lock ? " for update" : ""),
                (row, index) -> new StoredRow(row.getString("id"), row.getString("registered_client_id"), row.getString("principal_name"),
                        row.getObject("grant_id", Long.class), row.getLong("row_version"), row.getString("encrypted_payload")), id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** 检查密文归属并注入数据库版本，版本不放进密文或对外 API。 */
    private OAuth2Authorization decode(StoredRow row) {
        OAuth2Authorization value = codec.decode(row.id(), row.encrypted());
        if (!row.client().equals(value.getRegisteredClientId()) || !row.principal().equals(value.getPrincipalName())
                || !Objects.equals(row.grantId(), value.getAttribute(McpOAuthAttributes.GRANT_ID))) {
            throw new IllegalStateException("OAuth 密文与索引归属不一致");
        }
        return OAuth2Authorization.from(value).attribute(McpOAuthAttributes.STORAGE_VERSION, row.version()).build();
    }

    /** 读取哈希行，必要时持有索引锁直到整个兑换事务完成。 */
    private IndexRow index(String hash, boolean lock) {
        List<IndexRow> rows = jdbc.query("select * from mcp_token_index where token_hash=?" + (lock ? " for update" : ""),
                (row, index) -> indexRow(row), hash);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** 将 JDBC 索引行映射为不含凭据的状态对象。 */
    private static IndexRow indexRow(java.sql.ResultSet row) throws java.sql.SQLException {
        return new IndexRow(row.getString("token_hash"), row.getString("token_type"), row.getString("authorization_id"),
                row.getObject("grant_id", Long.class), row.getString("family_id"), row.getString("status"), row.getTimestamp("expires_at").toInstant());
    }

    /** 仅锁定 grant，客户端状态独立读取，避免 JOIN FOR UPDATE 与 CIMD 客户端更新形成反向锁序。 */
    private GrantBinding lockGrant(Long id) {
        List<GrantBinding> rows = jdbc.query("""
                select * from mcp_grant where grant_id=? for update
                """, (row, index) -> new GrantBinding(row.getLong("user_id"), row.getString("registered_client_id"),
                row.getString("resource_uri"), row.getString("status"), null, row.getLong("authorization_version")), id);
        if (rows.isEmpty()) { return null; }
        GrantBinding grant = rows.get(0);
        List<String> states = jdbc.query("select status from mcp_client_ext where registered_client_id=?", (row, index) -> row.getString(1), grant.client());
        return new GrantBinding(grant.userId(), grant.client(), grant.resource(), grant.status(),
                states.size() == 1 ? states.get(0) : null, grant.version());
    }

    /** 尚未同意的请求同样不能使用已停用客户端。 */
    private boolean clientActive(String client) {
        List<String> states = jdbc.query("select status from mcp_client_ext where registered_client_id=?",
                (row, index) -> row.getString(1), client);
        return states.size() == 1 && "0".equals(states.get(0));
    }

    /** 加密授权行的必要索引。 */
    private record StoredRow(String id, String client, String principal, Long grantId, long version, String encrypted) { }

    /** 哈希索引的当前状态，不含凭据明文。 */
    private record IndexRow(String hash, String type, String authorizationId, Long grantId, String familyId, String status, Instant expiresAt) { }

    /** 写入当前凭据索引所需的最小元数据。 */
    private record IndexedToken(String hash, String type, String status, Instant issuedAt, Instant expiresAt) { }

    /** 数据库当前授权绑定。 */
    private record GrantBinding(Long userId, String client, String resource, String status, String clientStatus, Long version) { }
}
