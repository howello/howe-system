package com.howe.mcp.service;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;
import java.util.List;

/** 当前用户的 MCP 连接与调用记录查询服务接口。 */
public interface McpSelfService {
    /** 分页返回本人连接；客户端密钥、授权载荷、令牌索引和权限快照均不进入响应。 */
    Page<ConnectionView> connections(Long userId, int page, int pageSize);

    /** 调用记录只公开时间、结果和受限元数据，不返回摘要或任何原始参数/结果。 */
    Page<CallView> calls(Long userId, int page, int pageSize);

    /** 独立自助页面分页契约，不复用要求后台权限的分页或身份参数。 */
    public record Page<T>(List<T> items, long total, int page, int pageSize) {
        /** 固定本次返回列表，不向调用者暴露可变查询结果。 */
        public Page {
            items = List.copyOf(items);
        }
    }

    /** 本人连接的公开状态；授权范围是当前同意，不表示客户端自动获得了所有业务权限。 */
    public record ConnectionView(Long grantId, String clientId, String clientName, String registrationSource,
            String resource, String status, String clientStatus, List<String> scopes,
            @JsonFormat(shape = JsonFormat.Shape.STRING) Instant createdAt,
            @JsonFormat(shape = JsonFormat.Shape.STRING) Instant updatedAt,
            @JsonFormat(shape = JsonFormat.Shape.STRING) Instant lastUsedAt,
            @JsonFormat(shape = JsonFormat.Shape.STRING) Instant revokedAt) { }

    /** 无敏感正文的本人事件记录。 */
    public record CallView(Long logId, String requestId, String eventType, Long grantId, String toolName,
            String resultCode, long elapsedMs, @JsonFormat(shape = JsonFormat.Shape.STRING) Instant createdAt) { }
}
