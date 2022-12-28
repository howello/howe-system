package com.howe.admin.service.auth;

import com.baomidou.mybatisplus.extension.service.IService;
import com.github.pagehelper.PageInfo;
import com.howe.common.dto.role.UserDTO;

import java.util.List;

/**
 * <p>@Author lu
 * <p>@Date 2022/11/29 9:53 星期二
 * <p>@Version 1.0
 * <p>@Description 用户数据表操作类
 */
public interface UserService extends IService<UserDTO> {

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

    /**
     * 获取用户列表
     *
     * @param userDTO
     * @return
     */
    List<UserDTO> getUserList(UserDTO userDTO);

    /**
     * 分页获取用户列表
     *
     * @param userDTO
     * @return
     */
    PageInfo<UserDTO> getUserPage(UserDTO userDTO);

    /**
     * 更改用户状态
     *
     * @param userId
     * @return
     */
    boolean changeUserStatus(String userId);

    /**
     * 修改密码
     *
     * @param userDTO
     * @return
     */
    boolean resetUserPwd(UserDTO userDTO);

    /**
     * 更新用户信息
     *
     * @param userDTO
     * @return
     */
    Boolean updateUser(UserDTO userDTO);

    /**
     * 添加用户
     *
     * @param userDTO
     * @return
     */
    Boolean addUser(UserDTO userDTO);

    /**
     * 删除用户
     *
     * @param userIds
     * @return
     */
    Boolean delUser(String[] userIds);
}
