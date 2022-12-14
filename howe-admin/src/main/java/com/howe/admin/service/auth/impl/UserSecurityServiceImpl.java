package com.howe.admin.service.auth.impl;

import com.howe.admin.service.auth.UserService;
import com.howe.admin.service.menu.MenuService;
import com.howe.common.dto.login.LoginUserDTO;
import com.howe.common.dto.role.UserDTO;
import com.howe.common.enums.account.UserStatusEnum;
import com.howe.common.enums.exception.AdminExceptionEnum;
import com.howe.common.exception.child.AdminException;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * <p>@Author lu
 * <p>@Date 2022/11/29 9:29 星期二
 * <p>@Version 1.0
 * <p>@Description
 */
@Service
public class UserSecurityServiceImpl implements UserDetailsService {

    @Autowired
    private UserService userService;

    @Autowired
    private MenuService menuService;

    /**
     * 根据用户名定位用户。在实际实现中，搜索可能区分大小写或不区分大小写，具体取决于实现实例的配置方式。
     * 在这种情况下，返回的 <code>UserDetails<code> 对象的用户名可能与实际请求的不同。
     *
     * @param username the username identifying the user whose data is required.
     * @return a fully populated user record (never <code>null</code>)
     * @throws UsernameNotFoundException if the user could not be found or the user has no
     *                                   GrantedAuthority
     */
    @SneakyThrows
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserDTO user = userService.selectUserByUserName(username);
        if (user == null) {
            thr(AdminExceptionEnum.NOT_EXIST, username);
        }
        String status = user.getStatus();
        if (UserStatusEnum.ACCOUNT_EXPIRED.getCode().equals(status)) {
            thr(AdminExceptionEnum.ACCOUNT_EXPIRED, username);
        }
        if (UserStatusEnum.CREDENTIALS_EXPIRED.getCode().equals(status)) {
            thr(AdminExceptionEnum.CREDENTIALS_EXPIRED, username);
        }
        if (UserStatusEnum.LOCKED.getCode().equals(status)) {
            thr(AdminExceptionEnum.LOCKED, username);
        }
        if (UserStatusEnum.BLOCKED.getCode().equals(status)) {
            thr(AdminExceptionEnum.BLOCKED, username);
        }
        if (UserStatusEnum.DELETE.getCode().equals(status)) {
            thr(AdminExceptionEnum.USER_DELETED, username);
        }
        return new LoginUserDTO(user.getUserId(), user.getDeptId(), user);
    }

    private void thr(AdminExceptionEnum adminExceptionEnum, String username) throws AdminException {
        throw new AdminException(
                adminExceptionEnum.getCode(),
                AdminException.assembleMsg(adminExceptionEnum, 3, "对不起，当前用户", username)
        );
    }
}
