package com.howe.mcp.service.impl;

import com.howe.common.core.domain.entity.SysRole;
import com.howe.common.core.domain.model.LoginUser;
import com.howe.common.tool.model.ToolAccess;
import com.howe.common.utils.SecurityUtils;
import com.howe.mcp.service.McpToolPolicyService;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 基于当前登录账号的权限与角色执行访问判定。
 *
 * <p>角色判断使用 {@code LoginUser.getUser().getRoles()} 的 {@code roleKey}；
 * 平台内置账号按 {@code SysUser.isAdmin()} 放行，这与"拥有 admin 角色键"不是同一概念。
 * 权限 {@code *:*:*} 只豁免权限集合，不绕过 scope、工具开关、授权状态或业务归属。</p>
 */
public final class McpToolPolicyServiceImpl implements McpToolPolicyService {

    /** 当前账号加载失败时拒绝，不缓存历史身份。 */
    @Override
    public boolean allowed(ToolAccess access) {
        Objects.requireNonNull(access);
        LoginUser loginUser;
        try {
            loginUser = SecurityUtils.getLoginUser();
        } catch (RuntimeException exception) {
            return false;
        }
        if (loginUser == null || loginUser.getUser() == null) {
            return false;
        }
        if (access.authenticatedOnly()
                && (loginUser.getUserId() == null || loginUser.getUsername() == null)) {
            return false;
        }
        Set<String> permissions = loginUser.getPermissions() == null ? Set.of() : loginUser.getPermissions();
        Set<String> roles = loginUser.getUser().getRoles() == null ? Set.of()
                : loginUser.getUser().getRoles().stream()
                        .filter(Objects::nonNull)
                        .map(SysRole::getRoleKey)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toUnmodifiableSet());
        return access.allows(permissions, roles, loginUser.getUser().isAdmin());
    }
}
