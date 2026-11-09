package com.howe.mcp.service.security;

import java.time.Instant;

/** 当前 HTTP 请求的服务端认证证明；仅保留哈希，不把明文 access token 放进协议会话。 */
public record McpBearerIdentity(McpInvocationContext invocation, String authorizationId, String tokenHash, Instant expiresAt) {
    public static final String REQUEST_ATTRIBUTE = "howe.mcp.bearer";
}
