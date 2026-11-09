package com.howe.mcp.service.impl;

import com.howe.mcp.service.McpSelfService;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;

/** 当前用户的连接与调用记录只读查询，所有 SQL 都强制绑定已认证用户主键。 */
@Slf4j
public final class McpSelfServiceImpl implements McpSelfService {
    private final JdbcOperations jdbc;
    private final OAuth2AuthorizationConsentService consents;

    /** 查询使用模块 JDBC，范围仍以唯一权威 Consent 为准。 */
    public McpSelfServiceImpl(JdbcOperations jdbc, OAuth2AuthorizationConsentService consents) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.consents = Objects.requireNonNull(consents);
    }

    /** 分页返回本人连接；客户端密钥、授权载荷、令牌索引和权限快照均不进入响应。 */
    @Override
    public Page<ConnectionView> connections(Long userId, int page, int pageSize) {
        long offset = offset(userId, page, pageSize);
        Long total = jdbc.queryForObject("select count(*) from mcp_grant where user_id=?", Long.class, userId);
        List<ConnectionView> items = jdbc.query("""
                select g.*,c.client_id,c.client_name,e.status client_status,e.registration_source,
                       (select max(l.create_time) from mcp_call_log l where l.grant_id=g.grant_id
                           and l.user_id=g.user_id and l.event_type='TOOL_CALL') last_used_at
                  from mcp_grant g join oauth2_registered_client c on c.id=g.registered_client_id
                  join mcp_client_ext e on e.registered_client_id=c.id
                 where g.user_id=? order by g.update_time desc,g.grant_id desc limit ? offset ?
                """, (row, index) -> {
                    var consent = consents.findById(row.getString("registered_client_id"), String.valueOf(userId));
                    List<String> scopes = consent == null ? List.of() : consent.getScopes().stream().sorted().toList();
                    return new ConnectionView(row.getLong("grant_id"), row.getString("client_id"), row.getString("client_name"),
                            row.getString("registration_source"), row.getString("resource_uri"), row.getString("status"),
                            row.getString("client_status"), scopes, instant(row.getTimestamp("create_time")),
                            instant(row.getTimestamp("update_time")), instant(row.getTimestamp("last_used_at")),
                            instant(row.getTimestamp("revoked_at")));
                }, userId, pageSize, offset);
        return new Page<>(items, total == null ? 0 : total, page, pageSize);
    }

    /** 调用记录只公开时间、结果和受限元数据，不返回摘要或任何原始参数/结果。 */
    @Override
    public Page<CallView> calls(Long userId, int page, int pageSize) {
        long offset = offset(userId, page, pageSize);
        Long total = jdbc.queryForObject("select count(*) from mcp_call_log where user_id=?", Long.class, userId);
        List<CallView> items = jdbc.query("""
                select log_id,request_id,event_type,grant_id,tool_name,result_code,elapsed_ms,create_time
                  from mcp_call_log where user_id=? order by create_time desc,log_id desc limit ? offset ?
                """, (row, index) -> new CallView(row.getLong("log_id"), row.getString("request_id"), row.getString("event_type"),
                row.getObject("grant_id", Long.class), row.getString("tool_name"), row.getString("result_code"),
                row.getLong("elapsed_ms"), instant(row.getTimestamp("create_time"))), userId, pageSize, offset);
        return new Page<>(items, total == null ? 0 : total, page, pageSize);
    }

    /** 限制单页及最大偏移；不能由请求传入用户身份。 */
    private static long offset(Long userId, int page, int pageSize) {
        if (userId == null || userId <= 0 || page < 1 || page > 10000 || pageSize < 1 || pageSize > 100) {
            throw new IllegalArgumentException("分页参数无效");
        }
        return (long) (page - 1) * pageSize;
    }

    /** 空的最近调用/撤销时间保留为空，不捏造使用记录。 */
    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

}
