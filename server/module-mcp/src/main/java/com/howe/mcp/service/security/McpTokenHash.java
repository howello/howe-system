package com.howe.mcp.service.security;

import cn.hutool.crypto.digest.DigestUtil;

/** opaque 凭据定位只使用 SHA-256，不将令牌明文放入数据库索引或日志。 */
public final class McpTokenHash {

    /** 工具类不允许实例化。 */
    private McpTokenHash() {
    }

    /** 对有界非空凭据生成固定长度小写十六进制索引。 */
    public static String of(String token) {
        if (token == null || token.isBlank() || token.length() > 8192) {
            throw new IllegalArgumentException("OAuth 凭据不能为空或超过长度限制");
        }
        return DigestUtil.sha256Hex(token);
    }
}
