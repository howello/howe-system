package com.howe.mcp.service;


/** OAuth 客户端凭据撤销服务接口。 */
public interface McpTokenRevocationService {
    /** access 只撤销自身，refresh 撤销整个对应家族；不删除 Consent 或撤销其它独立家族。 */
    void revoke(String registeredClientId, String token);
}
