package com.howe.mcp.service.security;

import java.util.Map;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;

/** 由专属撤销 converter 创建的内部类型，不能通过 token 表单中的标记伪造撤销分支。 */
@lombok.extern.slf4j.Slf4j
public final class McpPublicRevocationClientToken extends OAuth2ClientAuthenticationToken {
    private static final long serialVersionUID = 1L;

    /** 公共客户端只提供登记标识；令牌归属在撤销事务内独立验证。 */
    public McpPublicRevocationClientToken(String clientId) {
        super(clientId, ClientAuthenticationMethod.NONE, null, Map.of());
    }
}
