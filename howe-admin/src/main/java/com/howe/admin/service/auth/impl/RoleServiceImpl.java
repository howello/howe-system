package com.howe.admin.service.auth.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.howe.admin.dao.RoleDAO;
import com.howe.admin.service.auth.RoleService;
import com.howe.common.dto.role.RoleDTO;
import com.howe.common.dto.role.UserDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * <p>@Author lu
 * <p>@Date 2022/12/1 18:19 星期四
 * <p>@Version 1.0
 * <p>@Description TODO
 */
@Service
public class RoleServiceImpl extends ServiceImpl<RoleDAO, RoleDTO> implements RoleService {

    @Autowired
    private RoleDAO roleDAO;

    /**
     * 获取用户的角色
     *
     * @param user
     * @return
     */
    @Override
    public Set<String> getRolePermission(UserDTO user) {
        Set<String> roles = new HashSet<>();
        if (user.isAdmin()) {
            roles.add("admin");
        } else {
            roles.addAll(this.selectRolePermissionByUserId(user.getUserId()));
        }
        return roles;
    }

    /**
     * @param userId
     * @return
     */
    @Override
    public Set<String> selectRolePermissionByUserId(Long userId) {
        List<RoleDTO> roleList = roleDAO.selectRoleListByUserId(userId);
        Set<String> rolePerm = roleList.stream()
                .filter(Objects::nonNull)
                .map(role -> role.getRoleKey().trim().split(","))
                .flatMap(Arrays::stream)
                .collect(Collectors.toSet());
        return rolePerm;
    }

    /**
     * 根据用户id查询权限列表
     *
     * @param userId
     * @return
     */
    @Override
    public List<RoleDTO> selectRoleListByUserId(Long userId) {
        return roleDAO.selectRoleListByUserId(userId);
    }
}
