package com.howe.admin.service.menu;

import com.baomidou.mybatisplus.extension.service.IService;
import com.howe.common.dto.menu.MenuDTO;

import java.util.List;

/**
 * <p>@Author lu
 * <p>@Date 2022/11/29 14:19 星期二
 * <p>@Version 1.0
 * <p>@Description TODO
 */
public interface MenuService extends IService<MenuDTO> {

    /**
     * 根据权限获取菜单列表
     *
     * @return
     */
    List<MenuDTO> getMenuListWithPermission();

    /**
     * 获取菜单列表
     *
     * @param menuDTO
     * @return
     */
    List<MenuDTO> getMenuList(MenuDTO menuDTO);

    /**
     * 根据角色id获取菜单列表
     *
     * @param roleId
     * @return
     */
    List<MenuDTO> getMenuListByRoleId(String roleId);
}
