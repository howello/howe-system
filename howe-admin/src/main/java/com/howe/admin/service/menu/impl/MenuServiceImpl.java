package com.howe.admin.service.menu.impl;

import cn.hutool.core.date.DateTime;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.howe.admin.dao.auth.RoleMenuDAO;
import com.howe.admin.dao.menu.MenuDAO;
import com.howe.admin.service.menu.MenuService;
import com.howe.common.dto.menu.MenuDTO;
import com.howe.common.dto.role.UserDTO;
import com.howe.common.enums.exception.AdminExceptionEnum;
import com.howe.common.exception.child.AdminException;
import com.howe.common.utils.id.IdGeneratorUtil;
import com.howe.common.utils.mybatis.MybatisUtils;
import com.howe.common.utils.token.UserInfoUtils;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
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
    private final MenuDAO menuDAO;

    private final UserInfoUtils userInfoUtils;

    private final IdGeneratorUtil idGeneratorUtil;

    private final RoleMenuDAO roleMenuDAO;

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
     * 根据角色id获取菜单列表
     *
     * @param roleId
     * @return
     */
    @SneakyThrows
    @Override
    public List<MenuDTO> getMenuListByRoleId(String roleId) {
        if (StringUtils.isBlank(roleId)) {
            throw new AdminException(AdminExceptionEnum.PARAMETER_INVALID);
        }
        List<MenuDTO> roleMenuList = roleMenuDAO.selectMenuListByRoleId(roleId);
        return roleMenuList;
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
        menu.setMenuId(idGeneratorUtil.nextStr());
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
        menuDTO.setParentId(String.valueOf(id));
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
        Map<String, List<MenuDTO>> parentMap = menuList.stream()
                .collect(Collectors.groupingBy(MenuDTO::getParentId));
        return menuList.stream()
                .peek(m -> m.setChildren(parentMap.getOrDefault(m.getMenuId(), null)))
                .filter(MenuDTO::isTopMenu)
                .filter(m -> StringUtils.isNotBlank(m.getMenuId()))
                .collect(Collectors.toList());
    }
}
