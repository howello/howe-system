package com.howe.mcp.service;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;
import java.util.List;

/** 当前用户的 MCP 连接查询服务接口。 */
public interface McpSelfService {
    /** 分页返回本人连接；客户端密钥、授权载荷、令牌索引和权限快照均不进入响应。 */
    Page<ConnectionView> connections(Long userId, int page, int pageSize);

    /** 独立自助页面分页契约，不复用要求后台权限的分页或身份参数。 */
    public record Page<T>(List<T> items, long total, int page, int pageSize) {
        /** 固定本次返回列表，不向调用者暴露可变查询结果。 */
        public Page {
            items = List.copyOf(items);
        }
    }

    /** 本人连接的公开状态。 */
    public record ConnectionView(Long grantId, String clientId, String clientName, String registrationSource,
            String resource, String status, String clientStatus,
            @JsonFormat(shape = JsonFormat.Shape.STRING) Instant createdAt,
            @JsonFormat(shape = JsonFormat.Shape.STRING) Instant updatedAt,
            @JsonFormat(shape = JsonFormat.Shape.STRING) Instant revokedAt) { }

}
