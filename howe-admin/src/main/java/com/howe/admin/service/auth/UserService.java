package com.howe.admin.service.auth;

import com.howe.common.dto.role.UserDTO;

/**
 * <p>@Author lu
 * <p>@Date 2022/11/29 9:53 星期二
 * <p>@Version 1.0
 * <p>@Description 用户数据表操作类
 */
public interface UserService {

    /**
     * 根据用户名查询用户
     *
     * @param username
     * @return
     */
    UserDTO selectUserByUserName(String username);

    /**
     * 校验用户名是否唯一
     *
     * @param username
     * @return true-- 唯一
     */
    Boolean checkUserNameUnique(String username);

    /**
     * 注册
     *
     * @param userDTO
     * @return
     */
    Boolean register(UserDTO userDTO);
}
