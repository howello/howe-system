package com.howe.mcp.service.security;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** 当前实际支持的授权范围；客户端登记不能自行添加业务权限。 */
public final class McpScopes {
    public static final String CONNECT = "mcp:connect";
    public static final String OFFLINE_ACCESS = "offline_access";
    public static final String BLOG_READ = "blog:read";
    public static final String SYSTEM_SELF_READ = "system:self:read";
    public static final String MEAL_READ = "meal:read";
    public static final String SHARE_READ = "share:read";
    public static final String AI_CATALOG_READ = "ai:catalog:read";
    public static final String AI_LOGS_READ = "ai:logs:read";
    public static final String SYSTEM_NOTICE_READ = "system:notice:read";
    public static final String SYSTEM_AUDIT_READ = "system:audit:read";
    public static final String BLOG_WRITE = "blog:write";
    public static final String SYSTEM_NOTICE_WRITE = "system:notice:write";
    public static final String MEAL_WRITE = "meal:write";
    public static final String MEAL_KITCHEN_READ = "meal:kitchen:read";
    public static final String MEAL_KITCHEN_WRITE = "meal:kitchen:write";
    public static final String MEAL_PROPOSAL_AUDIT = "meal:proposal:audit";
    public static final String MEAL_MEMBER_READ = "meal:member:read";

    /** 按阶段增量放开；只有本阶段真正发布工具使用的 scope 才加入，顺序稳定便于元数据展示。 */
    public static final Set<String> SUPPORTED = Collections.unmodifiableSet(new LinkedHashSet<>(Set.of(
            CONNECT, OFFLINE_ACCESS, BLOG_READ, SYSTEM_SELF_READ,
            MEAL_READ, SHARE_READ, AI_CATALOG_READ, AI_LOGS_READ, SYSTEM_NOTICE_READ, SYSTEM_AUDIT_READ,
            BLOG_WRITE, SYSTEM_NOTICE_WRITE,
            MEAL_WRITE, MEAL_KITCHEN_READ, MEAL_KITCHEN_WRITE, MEAL_PROPOSAL_AUDIT, MEAL_MEMBER_READ)));

    /** 常量类不允许实例化。 */
    private McpScopes() {
    }
}
