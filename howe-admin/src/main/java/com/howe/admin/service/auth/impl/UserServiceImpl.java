package com.howe.admin.service.auth.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.howe.admin.dao.auth.UserDAO;
import com.howe.admin.service.auth.UserService;
import com.howe.common.dto.role.UserDTO;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * <p>@Author lu
 * <p>@Date 2022/11/29 9:54 星期二
 * <p>@Version 1.0
 * <p>@Description TODO
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserDAO, UserDTO> implements UserService {
    private final UserDAO userDAO;


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
}
