package com.howe.mcp.service.spi;

import com.howe.common.core.domain.model.LoginBody;
import com.howe.common.core.domain.model.LoginUser;

/**
 * 汇聚层提供的账号桥接，隔离 MCP 与 framework 的 Maven 依赖。
 */
public interface McpAccountBridge {

    /**
     * 执行完整账号认证及人机校验，不套用管理端角色门槛，也不签发原后台 JWT。
     *
     * @param body 登录表单
     * @return 当前已认证用户
     */
    LoginUser authenticate(LoginBody body);

    /**
     * 按已认证身份加载当前账号、家庭与权限，不重复进行密码验证。
     *
     * @param userId 已认证的用户 ID
     * @return 当前正常账号；停用、删除或不存在时应拒绝
     */
    LoginUser loadActiveUser(Long userId);
}
