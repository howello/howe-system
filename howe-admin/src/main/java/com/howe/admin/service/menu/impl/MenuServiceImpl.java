package com.howe.admin.service.menu.impl;

import cn.hutool.core.date.DateTime;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.howe.admin.dao.menu.MenuDAO;
import com.howe.admin.service.auth.UserService;
import com.howe.admin.service.menu.MenuService;
import com.howe.common.dto.menu.MenuDTO;
import com.howe.common.dto.role.UserDTO;
import com.howe.common.enums.exception.AdminExceptionEnum;
import com.howe.common.exception.child.AdminException;
import com.howe.common.utils.mybatis.MybatisUtils;
import com.howe.common.utils.token.UserInfoUtils;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

/**
 * <p>@Author lu
 * <p>@Date 2022/11/29 14:19 星期二
 * <p>@Version 1.0
 * <p>@Description TODO
 */
@Service
@RequiredArgsConstructor
public class MenuServiceImpl extends ServiceImpl<MenuDAO, MenuDTO> implements MenuService {
    private final UserService userService;

    private final MenuDAO menuDAO;

    private final UserInfoUtils userInfoUtils;

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
            userId = userInfoUtils.getUserId();
        } catch (Exception e) {
            return null;
        }
        List<MenuDTO> menuList = menuDAO.selectMenuListByUserId(userId);
        return formTree(menuList);
    }

    /**
     * 获取菜单列表
     *
     * @param menuDTO
     * @return
     */
    @Override
    public List<MenuDTO> getMenuList(MenuDTO menuDTO) {
        QueryWrapper<MenuDTO> qw = MybatisUtils.assembleQueryWrapper(menuDTO);
        List<MenuDTO> menuList = menuDAO.selectList(qw);
        return formTree(menuList);
    }

    /**
     * 插入一条记录（选择字段，策略插入）
     *
     * @param menu 实体对象
     */
    @Override
    public boolean save(MenuDTO menu) {
        UserDTO userInfo = userInfoUtils.getUserInfo();
        String userName = userInfo.getUserName();
        DateTime now = DateTime.now();
        menu.setCreateBy(userName);
        menu.setCreateTime(now);
        menu.setUpdateBy(userName);
        menu.setUpdateTime(now);
        return super.save(menu);
    }

    /**
     * 根据 ID 删除
     *
     * @param id 主键ID
     */
    @SneakyThrows
    @Override
    public boolean removeById(Serializable id) {
        MenuDTO menuDTO = new MenuDTO();
        menuDTO.setParentId((Long) id);
        QueryWrapper<MenuDTO> qw = MybatisUtils.assembleQueryWrapper(menuDTO);
        List<MenuDTO> menuDTOList = menuDAO.selectList(qw);
        if (CollectionUtils.isNotEmpty(menuDTOList)) {
            throw new AdminException(AdminExceptionEnum.THERE_ARE_SUBMENUS);
        }
        return super.removeById(id);
    }

    /**
     * 组装菜单为树
     *
     * @param menuList
     * @return
     */
    private List<MenuDTO> formTree(List<MenuDTO> menuList) {
        Map<Long, List<MenuDTO>> parentMap = menuList.stream()
                .collect(Collectors.groupingBy(MenuDTO::getParentId));
        return menuList.stream()
                .peek(m -> m.setChildren(parentMap.getOrDefault(m.getMenuId(), null)))
                .filter(m -> m.getParentId() == 0L)
                .filter(m -> m.getMenuId() != 0L)
                .collect(Collectors.toList());
    }
}
