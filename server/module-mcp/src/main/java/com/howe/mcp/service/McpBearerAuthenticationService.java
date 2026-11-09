package com.howe.mcp.service;

import com.howe.mcp.service.security.McpBearerIdentity;

/** 不透明 Bearer 凭据认证服务接口。 */
public interface McpBearerAuthenticationService {
    /** access 只用于资源认证，不把 refresh/code/state 当作访问凭据。 */
    McpBearerIdentity authenticate(String token, String remoteIp);

    /** SSE/会话清理无需保存明文 token；重新检查哈希状态、当前账号和授权。 */
    boolean isCurrent(McpBearerIdentity identity);
}
