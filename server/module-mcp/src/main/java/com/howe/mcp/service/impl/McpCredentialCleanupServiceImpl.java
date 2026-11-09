package com.howe.mcp.service.impl;

import com.howe.mcp.service.McpCredentialCleanupService;

import com.howe.common.config.ConfigProvider;
import com.howe.common.constant.ConfigConstants;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.transaction.support.TransactionTemplate;

/** 有界清理过期凭据家族和从未授权的登记；不删除历史 grant 或恢复任何凭据。 */
@Slf4j
@RequiredArgsConstructor
public final class McpCredentialCleanupServiceImpl implements McpCredentialCleanupService {
    private final JdbcOperations jdbc;
    private final TransactionTemplate transactions;
    private final ConfigProvider config;
    private final Clock clock;
    private static final int TOKEN_LIMIT = 5000;
    private static final int FAMILY_BATCH = 500;
    private static final String EXPIRED = "expires_at<? and (family_expires_at is null or family_expires_at<?)";

    /** 单轮最多 100 个授权载荷、5000 条索引及 100 个闲置客户端；五秒后不再开启新事务。 */
    @Override
    public Statistics clean() {
        int grace = parameter(ConfigConstants.MCP_CREDENTIAL_CLEANUP_GRACE_MINUTES, 60, 1, 1440);
        int unused = parameter(ConfigConstants.MCP_CLIENT_UNUSED_RETENTION_DAYS, 30, 1, 3650);
        Timestamp expired = Timestamp.from(clock.instant().minus(Duration.ofMinutes(grace)));
        Timestamp unusedBefore = Timestamp.from(clock.instant().minus(Duration.ofDays(unused)));
        long started = System.nanoTime();
        int authorizations = 0, tokens = 0, clients = 0;
        List<AuthorizationRow> candidates = jdbc.query("""
                select a.id,a.grant_id from oauth2_authorization a where a.update_time<?
                  and not exists (select 1 from mcp_token_index t where t.authorization_id=a.id
                      and (t.expires_at>=? or t.family_expires_at>=?))
                 order by a.update_time,a.id limit 100
                """, (row, index) -> new AuthorizationRow(row.getString(1), row.getObject(2, Long.class)), expired, expired, expired);
        for (AuthorizationRow candidate : candidates) {
            if (tokens >= TOKEN_LIMIT || elapsed(started)) { break; }
            int remaining = Math.min(FAMILY_BATCH, TOKEN_LIMIT - tokens);
            Result result = transactions.execute(status -> authorization(candidate, expired, remaining));
            if (result != null) { authorizations += result.authorizations(); tokens += result.tokens(); }
        }
        if (tokens < TOKEN_LIMIT && !elapsed(started)) {
            List<TokenRow> orphans = jdbc.query("""
                    select token_hash,authorization_id,grant_id from mcp_token_index t where
                    """ + EXPIRED + """
                      and not exists(select 1 from oauth2_authorization a where a.id=t.authorization_id)
                     order by expires_at,token_hash limit ?
                    """, (row, index) -> new TokenRow(row.getString(1), row.getString(2), row.getObject(3, Long.class)),
                    expired, expired, Math.min(500, TOKEN_LIMIT - tokens));
            for (TokenRow orphan : orphans) {
                if (elapsed(started)) { break; }
                Integer deleted = transactions.execute(status -> orphan(orphan, expired));
                tokens += deleted == null ? 0 : deleted;
            }
        }
        if (!elapsed(started)) {
            List<String> unusedClients = jdbc.query("""
                    select e.registered_client_id from mcp_client_ext e
                     where e.status='0' and e.metadata_valid='Y' and e.create_time<? and e.update_time<?
                       and (e.last_used_at is null or e.last_used_at<?)
                       and not exists(select 1 from mcp_grant g where g.registered_client_id=e.registered_client_id)
                       and not exists(select 1 from oauth2_authorization a where a.registered_client_id=e.registered_client_id)
                       and not exists(select 1 from oauth2_authorization_consent c where c.registered_client_id=e.registered_client_id)
                     order by e.update_time,e.registered_client_id limit 100
                    """, (row, index) -> row.getString(1), unusedBefore, unusedBefore, unusedBefore);
            for (String client : unusedClients) {
                if (elapsed(started)) { break; }
                Integer deleted = transactions.execute(status -> client(client, unusedBefore));
                clients += deleted == null ? 0 : deleted;
            }
        }
        return new Statistics(authorizations, tokens, clients);
    }

    /** 使用与兑换一致的 grant→authorization→token 锁序，绑定已变化的候选留待下次。 */
    private Result authorization(AuthorizationRow expected, Timestamp cutoff, int limit) {
        lockGrant(expected.grantId());
        List<AuthorizationRow> rows = jdbc.query("select id,grant_id from oauth2_authorization where id=? and update_time<? for update",
                (row, index) -> new AuthorizationRow(row.getString(1), row.getObject(2, Long.class)), expected.id(), cutoff);
        if (rows.isEmpty() || !Objects.equals(expected.grantId(), rows.get(0).grantId())) { return new Result(0, 0); }
        Long live = jdbc.queryForObject("select count(*) from mcp_token_index where authorization_id=? and (expires_at>=? or family_expires_at>=?)",
                Long.class, expected.id(), cutoff, cutoff);
        if (live == null || live > 0) { return new Result(0, 0); }
        List<String> hashes = jdbc.query("select token_hash from mcp_token_index where authorization_id=? order by token_hash limit ? for update",
                (row, index) -> row.getString(1), expected.id(), limit);
        int deleted = 0;
        if (!hashes.isEmpty()) {
            List<Object> values = new java.util.ArrayList<>();
            values.add(expected.id()); values.addAll(hashes);
            String placeholders = String.join(",", java.util.Collections.nCopies(hashes.size(), "?"));
            deleted = jdbc.update("delete from mcp_token_index where authorization_id=? and token_hash in (" + placeholders + ")", values.toArray());
        }
        Long left = jdbc.queryForObject("select count(*) from mcp_token_index where authorization_id=?", Long.class, expected.id());
        int removed = left != null && left == 0 ? jdbc.update("delete from oauth2_authorization where id=?", expected.id()) : 0;
        return new Result(removed, deleted);
    }

    /** 载荷已被拒绝/撤销删除的索引仍必须等自身及家族绝对过期，再复核当前归属。 */
    private int orphan(TokenRow expected, Timestamp cutoff) {
        lockGrant(expected.grantId());
        if (!jdbc.queryForList("select id from oauth2_authorization where id=? for update", expected.authorizationId()).isEmpty()) { return 0; }
        List<TokenRow> rows = jdbc.query("select token_hash,authorization_id,grant_id from mcp_token_index where token_hash=? for update",
                (row, index) -> new TokenRow(row.getString(1), row.getString(2), row.getObject(3, Long.class)), expected.hash());
        if (rows.isEmpty() || !rows.get(0).equals(expected)) { return 0; }
        return jdbc.update("delete from mcp_token_index where token_hash=? and " + EXPIRED, expected.hash(), cutoff, cutoff);
    }

    /** 登记清理先锁共享配额，再锁客户端；授权创建/确认和 CIMD 更新采用相同客户端锁。 */
    private int client(String id, Timestamp cutoff) {
        List<String> quota = jdbc.query("select config_value from sys_config where config_key=? for update",
                (row, index) -> row.getString(1), ConfigConstants.MCP_REGISTRATION_MAX_CLIENTS);
        if (quota.size() != 1) { throw new IllegalStateException("登记配额配置缺失，停止清理客户端"); }
        if (jdbc.queryForList("select id from oauth2_registered_client where id=? for update", id).isEmpty()) { return 0; }
        List<String> current = jdbc.query("""
                select registered_client_id from mcp_client_ext where registered_client_id=? and status='0' and metadata_valid='Y'
                  and create_time<? and update_time<? and (last_used_at is null or last_used_at<?) for update
                """, (row, index) -> row.getString(1), id, cutoff, cutoff, cutoff);
        if (current.isEmpty()) { return 0; }
        for (String table : List.of("mcp_grant", "oauth2_authorization", "oauth2_authorization_consent")) {
            // 表名完全来自代码白名单。历史 grant 永不自动删除，因此曾授权的客户端永久排除在此清理之外。
            Long count = jdbc.queryForObject("select count(*) from " + table + " where registered_client_id=?", Long.class, id);
            if (count == null || count > 0) { return 0; }
        }
        int deleted = jdbc.update("delete from mcp_client_ext where registered_client_id=?", id);
        jdbc.update("delete from oauth2_registered_client where id=?", id);
        return deleted;
    }
    /** 只锁单个 grant，不通过 JOIN 反向锁定客户端。 */
    private void lockGrant(Long id) {
        if (id != null) { jdbc.queryForList("select grant_id from mcp_grant where grant_id=? for update", id); }
    }
    /** 非法在线参数拒绝整轮清理，不能自动缩短安全保留期。 */
    private int parameter(String key, int fallback, int min, int max) {
        String value = config.getPlainValue(key);
        int parsed = value == null || value.isBlank() ? fallback : Integer.parseInt(value);
        if (parsed < min || parsed > max) { throw new IllegalArgumentException("MCP 清理参数无效"); }
        return parsed;
    }
    /** 时间预算约束下一笔事务，不宣称能强制中断已开始的数据库语句。 */
    private static boolean elapsed(long started) { return System.nanoTime() - started >= TimeUnit.SECONDS.toNanos(5); }
    /** 最小授权索引，不解密载荷。 */
    private record AuthorizationRow(String id, Long grantId) { }
    /** 孤立索引的服务器归属，不包含凭据。 */
    private record TokenRow(String hash, String authorizationId, Long grantId) { }
    /** 单个家族事务实际删除数。 */
    private record Result(int authorizations, int tokens) { }

}
