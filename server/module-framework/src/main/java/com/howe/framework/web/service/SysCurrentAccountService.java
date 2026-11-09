package com.howe.framework.web.service;

import com.howe.common.core.domain.entity.SysUser;
import com.howe.common.core.domain.model.LoginUser;
import com.howe.system.service.ISysUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/** 按稳定用户主键刷新正常账号、部门、角色和权限，不调用密码验证入口。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysCurrentAccountService {
    private final ISysUserService users;
    private final SysPermissionService permissions;

    /** 仅正常且未删除的账号可建立当前身份；不缓存历史权限或家庭归属。 */
    public LoginUser loadActiveUser(Long userId) {
        if (userId == null || userId <= 0) {
            throw new AccessDeniedException("账号不可用");
        }
        SysUser user = users.selectUserById(userId);
        if (user == null || !userId.equals(user.getUserId()) || !"0".equals(user.getStatus()) || !"0".equals(user.getDelFlag())) {
            throw new AccessDeniedException("账号不可用");
        }
        return new LoginUser(user.getUserId(), user.getDeptId(), user, permissions.getMenuPermission(user));
    }
}
