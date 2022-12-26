package com.howe.common.utils.token;

import com.howe.common.dto.login.LoginUserDTO;
import com.howe.common.dto.role.UserDTO;
import com.howe.common.enums.exception.CommonExceptionEnum;
import com.howe.common.exception.child.CommonException;
import lombok.SneakyThrows;
import org.springframework.stereotype.Component;

/**
 * <p>@Author lu
 * <p>@Date 2022/12/12 11:09 星期一
 * <p>@Version 1.0
 * <p>@Description 已登录用户信息
 */
@Component
public class UserInfoUtils {

    /**
     * 查询当前登录用户信息
     *
     * @return
     */
    public UserDTO getUserInfo() {
        return this.getLoginUser().getUser();
    }


    /**
     * 用户ID
     **/
    public String getUserId() {
        return getLoginUser().getUserId();
    }

    /**
     * 获取用户账户
     **/
    public String getUsername() {
        return getLoginUser().getUsername();
    }

    /**
     * 获取用户
     **/
    @SneakyThrows
    public LoginUserDTO getLoginUser() {
        Object principal = SecurityUtils.getAuthentication().getPrincipal();
        if (principal instanceof LoginUserDTO) {
            return (LoginUserDTO) principal;
        } else {
            throw new CommonException(CommonExceptionEnum.QUERY_USER_INFO_ERROR);
        }
    }
}
