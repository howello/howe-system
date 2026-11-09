package com.howe.mcp.service.spi;

import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;

/** 带重放失效等副作用的持久化查找必须先验证客户端归属，再执行状态变更。 */
public interface McpClientAuthorizationLookup {

    /** 不允许其它客户端通过提交已使用的 refresh 触发目标家族失效。 */
    OAuth2Authorization findByTokenForClient(String token, OAuth2TokenType type, String registeredClientId);
}
