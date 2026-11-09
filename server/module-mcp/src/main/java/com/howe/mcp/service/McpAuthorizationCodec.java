package com.howe.mcp.service;

import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;

/** OAuth 授权载荷编解码服务接口。 */
public interface McpAuthorizationCodec {
    /** 将最小协议快照加密；未知对象或缺少主密钥时直接失败。 */
    String encode(OAuth2Authorization authorization);

    /** 按预期记录 ID 解密并重建框架领域对象；密文交换、篡改或未知格式均拒绝。 */
    OAuth2Authorization decode(String expectedId, String encrypted);
}
