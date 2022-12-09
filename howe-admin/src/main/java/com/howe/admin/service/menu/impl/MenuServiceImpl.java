package com.howe.admin.service.menu.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.howe.admin.dao.menu.MenuDAO;
import com.howe.admin.service.auth.UserService;
import com.howe.admin.service.menu.MenuService;
import com.howe.common.dto.menu.MenuDTO;
import com.howe.common.dto.role.UserDTO;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * <p>@Author lu
 * <p>@Date 2022/11/29 14:19 星期二
 * <p>@Version 1.0
 * <p>@Description TODO
 */
@Service
public class MenuServiceImpl extends ServiceImpl<MenuDAO, MenuDTO> implements MenuService {
    @Autowired
    private UserService userService;

    @Autowired
    private MenuDAO menuDAO;


    /**
     * 获取用户对应的菜单
     *
     * @param userDTO
     * @return
     */
    @Override
    public Set<String> getMenuPermission(UserDTO userDTO) {
        Set<String> perms = new HashSet<>();
        if (userDTO.isAdmin()) {
            perms.add("*:*:*");
        } else {
            perms.addAll(this.selectMenuPermsByUserId(userDTO.getUserId()));
        }
        return perms;
    }

    /**
     * 根据用户ID查询权限
     *
     * @param userId 用户ID
     * @return 权限列表
     */
    @Override
    public Set<String> selectMenuPermsByUserId(Long userId) {
        List<String> perms = menuDAO.selectMenuPermsByUserId(userId);
        Set<String> permsSet = new HashSet<>();
        for (String perm : perms) {
            if (StringUtils.isNotEmpty(perm)) {
                permsSet.addAll(Arrays.asList(perm.trim().split(",")));
            }
        }
        return permsSet;
    }

    /**
     * 根据权限获取菜单列表
     *
     * @return
     */
    @Override
    public List<MenuDTO> getMenuListWithPermission() {
        Long userId;
        try {
            userId = userService.getUserId();
        } catch (Exception e) {
            return null;
        }
        List<MenuDTO> menuList = menuDAO.selectMenuListByUserId(userId);
        Map<Long, List<MenuDTO>> parentMap = menuList.stream()
                .collect(Collectors.groupingBy(MenuDTO::getParentId));
        menuList = menuList.stream()
                .peek(m -> m.setChildren(parentMap.getOrDefault(m.getMenuId(), null)))
                .filter(m -> m.getParentId() == 0L)
                .filter(m -> m.getMenuId() != 0L)
                .collect(Collectors.toList());
        return menuList;
    }

    /**
     * 根据权限分页查询菜单列表
     *
     * @return
     */
    @Override
    public PageInfo<MenuDTO> getMenuPageWithPermission(MenuDTO menu) {
        PageHelper.startPage(menu.getPageNum(), menu.getPageSize());
        PageInfo<MenuDTO> pageInfo = new PageInfo<>(this.getMenuListWithPermission());
        return pageInfo;
    }
}
