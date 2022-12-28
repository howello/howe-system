package com.howe.admin.service.auth.impl;

import cn.hutool.core.date.DateTime;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.howe.admin.dao.auth.UserDAO;
import com.howe.admin.service.auth.UserService;
import com.howe.common.dto.role.UserDTO;
import com.howe.common.enums.exception.AdminExceptionEnum;
import com.howe.common.exception.child.AdminException;
import com.howe.common.utils.id.IdGeneratorUtil;
import com.howe.common.utils.mybatis.MybatisUtils;
import com.howe.common.utils.token.SecurityUtils;
import com.howe.common.utils.token.UserInfoUtils;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

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

    private final UserInfoUtils userInfoUtils;

    private final IdGeneratorUtil idGeneratorUtil;


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
        UserDTO userInfo = userInfoUtils.getUserInfo();
        String userName = userInfo.getUserName();
        DateTime now = DateTime.now();
        userDTO.setPassword(SecurityUtils.encryptPassword(userDTO.getPassword()));
        userDTO.setUserId(idGeneratorUtil.nextStr());
        userDTO.setCreateBy(userName);
        userDTO.setCreateTime(now);
        userDTO.setUpdateBy(userName);
        userDTO.setUpdateTime(now);
        return userDAO.insert(userDTO) > 0;
    }

    /**
     * 获取用户列表
     *
     * @param userDTO
     * @return
     */
    @Override
    public List<UserDTO> getUserList(UserDTO userDTO) {
        QueryWrapper<UserDTO> qw = MybatisUtils.assembleQueryWrapper(userDTO);
        List<UserDTO> list = this.list(qw);
        return list.stream()
                .sorted(Comparator.comparing(UserDTO::getCreateTime))
                .collect(Collectors.toList());
    }

    /**
     * 分页获取用户列表
     *
     * @param userDTO
     * @return
     */
    @Override
    public PageInfo<UserDTO> getUserPage(UserDTO userDTO) {
        PageHelper.startPage(userDTO.getPageNum(), userDTO.getPageSize());
        PageInfo<UserDTO> page = new PageInfo<>(this.getUserList(userDTO));
        return page;
    }

    /**
     * 更改用户状态
     *
     * @param userId
     * @return
     */
    @SneakyThrows
    @Override
    public boolean changeUserStatus(String userId) {
        if (StringUtils.isBlank(userId)) {
            throw new AdminException(AdminExceptionEnum.PARAMETER_INVALID);
        }
        UserDTO userDTO = userDAO.selectById(userId);
        userDTO.setStatus("0".equals(userDTO.getStatus()) ? "1" : "0");
        int i = userDAO.updateById(userDTO);
        return i > 0;
    }

    /**
     * 修改密码
     *
     * @param userDTO
     * @return
     */
    @Override
    public boolean resetUserPwd(UserDTO userDTO) {
        String username = userInfoUtils.getUsername();
        userDTO.setPassword(SecurityUtils.encryptPassword(userDTO.getPassword()));
        userDTO.setUpdateBy(username);
        userDTO.setUpdateTime(DateTime.now());
        int i = userDAO.updateById(userDTO);
        return i > 0;
    }

    /**
     * 更新用户信息
     *
     * @param userDTO
     * @return
     */
    @Override
    public Boolean updateUser(UserDTO userDTO) {
        String username = userInfoUtils.getUsername();
        userDTO.setUpdateBy(username);
        userDTO.setUpdateTime(DateTime.now());
        return userDAO.updateById(userDTO) > 0;
    }

    /**
     * 添加用户
     *
     * @param userDTO
     * @return
     */
    @Override
    public Boolean addUser(UserDTO userDTO) {
        Boolean register = this.register(userDTO);
        return register;
    }

    /**
     * 删除用户
     *
     * @param userIds
     * @return
     */
    @SneakyThrows
    @Override
    public Boolean delUser(String[] userIds) {
        if (ArrayUtils.isEmpty(userIds)) {
            throw new AdminException(AdminExceptionEnum.PARAMETER_INVALID);
        }
        return userDAO.deleteBatchIds(Arrays.asList(userIds)) > 0;
    }
}
