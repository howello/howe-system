package com.howe.mcp.service.impl;

import com.howe.mcp.service.McpSelfService;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcOperations;

/** 当前用户的连接只读查询，所有 SQL 都强制绑定已认证用户主键。 */
@Slf4j
public final class McpSelfServiceImpl implements McpSelfService {
    private final JdbcOperations jdbc;

    /** 查询使用模块 JDBC。 */
    public McpSelfServiceImpl(JdbcOperations jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    /** 分页返回本人连接；客户端密钥、授权载荷、令牌索引和权限快照均不进入响应。 */
    @Override
    public Page<ConnectionView> connections(Long userId, int page, int pageSize) {
        long offset = offset(userId, page, pageSize);
        Long total = jdbc.queryForObject("select count(*) from mcp_grant where user_id=?", Long.class, userId);
        List<ConnectionView> items = jdbc.query("""
                select g.*,c.client_id,c.client_name,c.status client_status,c.registration_source
                  from mcp_grant g join oauth2_registered_client c on c.id=g.registered_client_id
                 where g.user_id=? order by g.update_time desc,g.grant_id desc limit ? offset ?
                """, (row, index) -> {
                    return new ConnectionView(row.getLong("grant_id"), row.getString("client_id"), row.getString("client_name"),
                            row.getString("registration_source"), row.getString("resource_uri"), row.getString("status"),
                            row.getString("client_status"), instant(row.getTimestamp("create_time")),
                            instant(row.getTimestamp("update_time")),
                            instant(row.getTimestamp("revoked_at")));
                }, userId, pageSize, offset);
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
