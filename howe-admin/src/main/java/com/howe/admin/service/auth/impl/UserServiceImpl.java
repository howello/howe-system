package com.howe.admin.service.auth.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.howe.admin.dao.auth.UserDAO;
import com.howe.admin.service.auth.UserService;
import com.howe.common.dto.login.LoginUserDTO;
import com.howe.common.dto.role.UserDTO;
import com.howe.common.enums.exception.CommonExceptionEnum;
import com.howe.common.exception.child.CommonException;
import com.howe.common.utils.token.SecurityUtils;
import lombok.SneakyThrows;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * <p>@Author lu
 * <p>@Date 2022/11/29 9:54 星期二
 * <p>@Version 1.0
 * <p>@Description TODO
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserDAO, UserDTO> implements UserService {
    @Autowired
    private UserDAO userDAO;


    /**
     * 根据用户名查询用户
     *
     * @param username
     * @return
     */
    @Override
    public UserDTO selectUserByUserName(String username) {
        QueryWrapper<UserDTO> qw = new QueryWrapper<>();
        qw.eq(UserDTO.COL_USER_NAME, username);
        UserDTO userDTO = userDAO.selectOne(qw);
        return userDTO;
    }

    /**
     * 校验用户名是否唯一
     *
     * @param username
     * @return true-- 唯一
     */
    @Override
    public Boolean checkUserNameUnique(String username) {
        QueryWrapper<UserDTO> qw = new QueryWrapper<>();
        qw.eq(UserDTO.COL_USER_NAME, username);
        List<UserDTO> userDTOS = userDAO.selectList(qw);
        if (CollectionUtils.isNotEmpty(userDTOS)) {
            return false;
        }
        return true;
    }

    /**
     * 注册
     *
     * @param userDTO
     * @return
     */
    @Override
    public Boolean register(UserDTO userDTO) {
        return userDAO.insert(userDTO) > 0;
    }

    /**
     * 查询当前登录用户信息
     *
     * @return
     */
    @Override
    public UserDTO getUserInfo() {
        return this.getLoginUser().getUser();
    }


    /**
     * 用户ID
     **/
    @Override
    public Long getUserId() {
        return getLoginUser().getUserId();
    }

    /**
     * 获取部门ID
     **/
    @Override
    public Long getDeptId() {
        return getLoginUser().getDeptId();
    }

    /**
     * 获取用户账户
     **/
    @Override
    public String getUsername() {
        return getLoginUser().getUsername();
    }

    /**
     * 获取用户
     **/
    @Override
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
