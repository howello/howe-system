package com.howe.admin.service.auth.impl;

import cn.hutool.core.date.DateTime;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.howe.admin.dao.auth.RoleDAO;
import com.howe.admin.dao.auth.RoleMenuDAO;
import com.howe.admin.service.auth.RoleService;
import com.howe.common.dto.role.RoleDTO;
import com.howe.common.dto.role.RoleMenuDTO;
import com.howe.common.dto.role.UserDTO;
import com.howe.common.utils.id.IdGeneratorUtil;
import com.howe.common.utils.mybatis.MybatisUtils;
import com.howe.common.utils.token.UserInfoUtils;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;

/**
 * <p>@Author lu
 * <p>@Date 2022/12/1 18:19 星期四
 * <p>@Version 1.0
 * <p>@Description TODO
 */
@Service
@RequiredArgsConstructor
public class RoleServiceImpl extends ServiceImpl<RoleDAO, RoleDTO> implements RoleService {

    private final RoleDAO roleDAO;

    private final RoleMenuDAO roleMenuDAO;

    private final IdGeneratorUtil idGeneratorUtil;

    private final UserInfoUtils userInfoUtils;

    /**
     * 获取全部权限
     *
     * @param role
     * @return
     */
    @Override
    public List<RoleDTO> getRoleList(RoleDTO role) {
        QueryWrapper<RoleDTO> qw = MybatisUtils.assembleQueryWrapper(role);
        qw.orderByAsc(RoleDTO.COL_ROLE_SORT);
        return roleDAO.selectList(qw);
    }

    /**
     * 分页获取权限
     *
     * @return
     */
    @Override
    public PageInfo<RoleDTO> getRolePage(RoleDTO role) {
        PageHelper.startPage(role.getPageNum(), role.getPageSize());
        PageInfo<RoleDTO> page = new PageInfo<>(this.getRoleList(role));
        return page;
    }

    /**
     * 插入一条记录（选择字段，策略插入）
     *
     * @param entity 实体对象
     */
    @Override
    public boolean save(RoleDTO role) {
        UserDTO userInfo = userInfoUtils.getUserInfo();
        String userName = userInfo.getUserName();
        DateTime now = DateTime.now();
        role.setRoleId(idGeneratorUtil.nextStr());
        role.setCreateBy(userName);
        role.setCreateTime(now);
        role.setUpdateBy(userName);
        role.setUpdateTime(now);
        this.updateRoleMenu(role);
        return super.save(role);
    }

    /**
     * 根据 ID 选择修改
     *
     * @param role 实体对象
     */
    @Override
    public boolean updateById(RoleDTO role) {
        updateRoleMenu(role);
        return super.updateById(role);
    }

    /**
     * 根据 ID 删除
     *
     * @param id 主键ID
     */
    @Override
    public boolean removeById(Serializable id) {
        updateRoleMenu(new RoleDTO((String) id));
        return super.removeById(id);
    }

    /**
     * 删除（根据ID 批量删除）
     *
     * @param idList 主键ID列表
     */
    @Override
    public boolean removeByIds(Collection<? extends Serializable> idList) {
        for (Serializable id : idList) {
            updateRoleMenu(new RoleDTO((String) id));
        }
        return super.removeByIds(idList);
    }

    /**
     * 更新角色-菜单关联表的信息
     *
     * @param role
     */
    private void updateRoleMenu(RoleDTO role) {
        QueryWrapper<RoleMenuDTO> qw = new QueryWrapper<>();
        qw.eq(RoleMenuDTO.COL_ROLE_ID, role.getRoleId());
        roleMenuDAO.delete(qw);

        List<String> menuIds = role.getMenuIds();
        if (CollectionUtils.isEmpty(role.getMenuIds())) {
            return;
        }
        RoleMenuDTO roleMenu = new RoleMenuDTO(role.getRoleId());
        for (String menuId : menuIds) {
            roleMenu.setMenuId(menuId);
            roleMenuDAO.insert(roleMenu);
        }
    }
}
