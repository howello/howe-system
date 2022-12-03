package com.howe.admin.service.auth;

import com.baomidou.mybatisplus.extension.service.IService;
import com.howe.common.dto.role.RoleDTO;
import com.howe.common.dto.role.UserDTO;

import java.util.List;
import java.util.Set;

/**
 * <p>@Author lu
 * <p>@Date 2022/12/1 18:19 星期四
 * <p>@Version 1.0
 * <p>@Description TODO
 */
public interface RoleService extends IService<RoleDTO> {


    /**
     * 获取用户的角色
     *
     * @param userDTO
     * @return
     */
    Set<String> getRolePermission(UserDTO userDTO);


    Set<String> selectRolePermissionByUserId(Long userId);

    /**
     * 根据用户id查询权限列表
     *
     * @param userId
     * @return
     */
    List<RoleDTO> selectRoleListByUserId(Long userId);
}
