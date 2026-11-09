package com.howe.mcp.service.security;

import java.util.Set;

/** 首期明确支持的授权范围；客户端登记不能自行添加业务权限。 */
public final class McpScopes {
    public static final String CONNECT = "mcp:connect";
    public static final String OFFLINE_ACCESS = "offline_access";
    public static final Set<String> SUPPORTED = Set.of(CONNECT, OFFLINE_ACCESS);

    /** 常量类不允许实例化。 */
    private McpScopes() {
    }
}
