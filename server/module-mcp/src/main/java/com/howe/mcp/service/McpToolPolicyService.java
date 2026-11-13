package com.howe.mcp.service;

import com.howe.common.tool.model.ToolAccess;

/**
 * 工具访问策略服务。
 *
 * <p>只负责当前账号的权限、角色与登录判定；scope、工具开关与授权状态仍由注册器判定。
 * 目录过滤与执行复核必须调用同一实现，避免"目录不可见但直接调用可执行"的差异。</p>
 */
public interface McpToolPolicyService {

    /**
     * 判断当前账号是否满足工具的访问要求。
     *
     * @param access 通用访问要求
     * @return 满足时返回 true
     */
    boolean allowed(ToolAccess access);
}
