package com.howe.admin.service.menu;

import com.baomidou.mybatisplus.extension.service.IService;
import com.howe.common.dto.menu.MenuDTO;
import com.howe.common.dto.role.UserDTO;

import java.util.List;
import java.util.Set;

/**
 * <p>@Author lu
 * <p>@Date 2022/11/29 14:19 星期二
 * <p>@Version 1.0
 * <p>@Description TODO
 */
public interface MenuService extends IService<MenuDTO> {


    /**
     * 获取用户对应的菜单
     *
     * @param userDTO
     * @return
     */
    Set<String> getMenuPermission(UserDTO userDTO);

    /**
     * 根据用户ID查询权限
     *
     * @param userId 用户ID
     * @return 权限列表
     */
    Set<String> selectMenuPermsByUserId(Long userId);

    /**
     * 根据权限获取菜单列表
     *
     * @return
     */
    List<MenuDTO> getMenuListWithPermission();
}
