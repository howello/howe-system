package com.howe.mcp.service.impl;

import com.howe.mcp.service.McpTokenRevocationService;

import com.howe.mcp.service.security.McpTokenHash;
import com.howe.mcp.service.security.McpAuditContext;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.transaction.support.TransactionTemplate;

/** RFC 7009 的哈希索引撤销事务，不调用带刷新重放副作用的兑换查找接口。 */
@Slf4j
public final class McpTokenRevocationServiceImpl implements McpTokenRevocationService {
    private final JdbcOperations jdbc;
    private final TransactionTemplate transactions;

    /** 与签发、轮换和 grant 撤销使用同一数据源事务。 */
    public McpTokenRevocationServiceImpl(JdbcOperations jdbc, TransactionTemplate transactions) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.transactions = Objects.requireNonNull(transactions);
    }

    /** access 只撤销自身，refresh 撤销整个对应家族；不删除 Consent 或撤销其它独立家族。 */
    @Override
    public void revoke(String registeredClientId, String token) {
        if (registeredClientId == null || registeredClientId.isBlank() || token == null || token.isBlank() || token.length() > 8192) {
            throw new OAuth2AuthenticationException(new OAuth2Error("invalid_request"));
        }
        String hash = McpTokenHash.of(token);
        transactions.executeWithoutResult(status -> {
            TokenRow initial = find(hash, false);
            if (initial == null) {
                return;
            }
            if (!registeredClientId.equals(initial.clientId())) {
                throw new OAuth2AuthenticationException(new OAuth2Error("invalid_client"));
            }
            // 与兑换固定保持 grant → authorization → token 顺序，不从 token 行开始加锁。
            if (initial.grantId() != null) {
                jdbc.queryForList("select grant_id from mcp_grant where grant_id=? for update", initial.grantId());
            }
            List<String> owners = jdbc.query("select registered_client_id from oauth2_authorization where id=? for update",
                    (row, index) -> row.getString(1), initial.authorizationId());
            TokenRow current = find(hash, true);
            if (owners.size() != 1 || current == null) {
                return;
            }
            if (!registeredClientId.equals(owners.get(0)) || !registeredClientId.equals(current.clientId())) {
                throw new OAuth2AuthenticationException(new OAuth2Error("invalid_client"));
            }
            Long auditUser = null;
            try { auditUser = Long.valueOf(current.principalName()); }
            catch (NumberFormatException exception) { /* 不将非数字主体猜测成系统账号。 */ }
            McpAuditContext.verified(auditUser, registeredClientId, current.grantId());
            if ("refresh_token".equals(current.type())) {
                jdbc.update("update mcp_token_index set status='REVOKED',update_time=current_timestamp where family_id=?",
                        current.authorizationId());
            } else {
                jdbc.update("update mcp_token_index set status='REVOKED',update_time=current_timestamp where token_hash=?", hash);
            }
        });
    }

    /** 未知、已清除载荷或非 access/refresh 的凭据按无效令牌处理，不返回其存在信息。 */
    private TokenRow find(String hash, boolean lock) {
        List<TokenRow> rows = jdbc.query("""
                select i.authorization_id,i.grant_id,i.token_type,a.registered_client_id,a.principal_name
                  from mcp_token_index i join oauth2_authorization a on a.id=i.authorization_id
                 where i.token_hash=? and i.token_type in ('access_token','refresh_token')
                """ + (lock ? " for update" : ""), (row, index) -> new TokenRow(row.getString("authorization_id"),
                row.getObject("grant_id", Long.class), row.getString("token_type"), row.getString("registered_client_id"),
                row.getString("principal_name")), hash);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** 数据库中不含令牌明文的归属与类型快照。 */
    private record TokenRow(String authorizationId, Long grantId, String type, String clientId, String principalName) { }
}
