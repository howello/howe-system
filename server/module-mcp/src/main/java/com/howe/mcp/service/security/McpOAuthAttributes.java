package com.howe.mcp.service.security;

import java.util.Set;

/** 需要在加密授权记录中保持的项目协议属性，避免序列化任意身份对象。 */
public final class McpOAuthAttributes {
    public static final String GRANT_ID = "mcp.grantId";
    public static final String AUTHORIZATION_VERSION = "mcp.authorizationVersion";
    public static final String RESOURCE = "mcp.resource";
    public static final String FAMILY_ID = "mcp.familyId";
    public static final String STORAGE_VERSION = "mcp.storageVersion";
    public static final Set<String> PERSISTED = Set.of(GRANT_ID, AUTHORIZATION_VERSION, RESOURCE, FAMILY_ID);

    /** 常量类不允许实例化。 */
    private McpOAuthAttributes() {
    }
}
