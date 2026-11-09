package com.howe.mcp.service.security;

import java.util.Set;

/**
 * 由服务端认证结果建立的不可变调用上下文，不接受模型参数中的身份字段。
 *
 * @param userId 用户 ID
 * @param username 用户名
 * @param deptId 当前家庭或部门
 * @param clientId OAuth 客户端
 * @param grantId 授权记录 ID
 * @param authorizationVersion 授权版本
 * @param scopes 已授权范围
 * @param resource 令牌绑定资源
 * @param requestId 服务端请求标识
 */
public record McpInvocationContext(Long userId, String username, Long deptId, String clientId, Long grantId,
        long authorizationVersion, Set<String> scopes, String resource, String requestId) {

    /** 拷贝授权范围，防止后续调用修改认证快照。 */
    public McpInvocationContext {
        scopes = Set.copyOf(scopes);
    }
}
