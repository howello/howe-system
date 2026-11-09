package com.howe.mcp.service;

import com.howe.mcp.service.spi.McpClientAuthorizationLookup;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;

/** 基于 JDBC 的 OAuth 授权存储服务接口。 */
public interface McpJdbcAuthorizationService extends OAuth2AuthorizationService, McpClientAuthorizationLookup {
    /** 在同一事务内校验绑定、保存密文和轮换索引；旧快照不得覆盖新令牌。 */
    void save(OAuth2Authorization authorization);

    /** 删除载荷但保留已撤销的历史哈希，避免清理导致旧凭据重新可用。 */
    void remove(OAuth2Authorization authorization);

    /** 返回带数据库版本的快照；管理查询不以此方法证明授权当前有效。 */
    OAuth2Authorization findById(String id);

    /** 协议会话只保存哈希，健康检查不能把哈希误当成明文令牌再次哈希或执行重放处理。 */
    boolean isAccessIndexActive(String hash, String authorizationId);

    /** 只用哈希定位，并在事务内重读索引和当前授权；刷新重放会撤销对应家族。 */
    OAuth2Authorization findByToken(String token, OAuth2TokenType tokenType);

    /** 客户端认证和兑换必须在重放处理之前限制授权归属；资源 Bearer 查找仍使用标准接口。 */
    OAuth2Authorization findByTokenForClient(String token, OAuth2TokenType tokenType, String registeredClientId);
}
