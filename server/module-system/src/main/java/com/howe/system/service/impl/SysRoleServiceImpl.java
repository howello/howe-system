package com.howe.system.service.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.howe.common.annotation.DataScope;
import com.howe.common.constant.FamilyRoleConstants;
import com.howe.common.constant.UserConstants;
import com.howe.common.core.domain.entity.SysRole;
import com.howe.common.exception.ServiceException;
import com.howe.common.utils.SecurityUtils;
import com.howe.common.utils.StringUtils;
import com.howe.common.utils.spring.SpringUtils;
import com.howe.system.domain.SysRoleDept;
import com.howe.system.domain.SysRoleMenu;
import com.howe.system.domain.SysUserRole;
import com.howe.system.mapper.SysRoleDeptMapper;
import com.howe.system.mapper.SysRoleMapper;
import com.howe.system.mapper.SysRoleMenuMapper;
import com.howe.system.mapper.SysUserRoleMapper;
import com.howe.system.service.ISysRoleService;

/**
 * 角色 业务层处理
 *
 * @author howe
 */
@Service
public class SysRoleServiceImpl implements ISysRoleService
{
    @Autowired
    private SysRoleMapper roleMapper;

    @Autowired
    private SysRoleMenuMapper roleMenuMapper;

    @Autowired
    private SysUserRoleMapper userRoleMapper;

    @Autowired
    private SysRoleDeptMapper roleDeptMapper;

    /**
     * 根据条件分页查询角色数据
     *
     * @param role 角色信息
     * @return 角色数据集合信息
     */
    @Override
    @DataScope(deptAlias = "d")
    public List<SysRole> selectRoleList(SysRole role)
    {
        return roleMapper.selectRoleList(role);
    }

    /**
     * 根据用户ID查询角色
     *
     * @param userId 用户ID
     * @return 角色列表
     */
    @Override
    public List<SysRole> selectRolesByUserId(Long userId)
    {
        List<SysRole> userRoles = roleMapper.selectRolePermissionByUserId(userId);
        List<SysRole> roles = selectRoleAll();
        for (SysRole role : roles)
        {
            for (SysRole userRole : userRoles)
            {
                if (role.getRoleId().longValue() == userRole.getRoleId().longValue())
                {
                    role.setFlag(true);
                    break;
                }
            }
        }
        return roles;
    }

    /**
     * 根据用户ID查询权限
     *
     * @param userId 用户ID
     * @return 权限列表
     */
    @Override
    public Set<String> selectRolePermissionByUserId(Long userId)
    {
        List<SysRole> perms = roleMapper.selectRolePermissionByUserId(userId);
        Set<String> permsSet = new HashSet<>();
        for (SysRole perm : perms)
        {
            if (StringUtils.isNotNull(perm))
            {
                permsSet.addAll(Arrays.asList(perm.getRoleKey().trim().split(",")));
            }
        }
        return permsSet;
    }

    /**
     * 查询所有角色
     *
     * @return 角色列表
     */
    @Override
    public List<SysRole> selectRoleAll()
    {
        return SpringUtils.getAopProxy(this).selectRoleList(new SysRole());
    }

    /**
     * 根据角色标识查询角色（不做数据权限过滤，供匿名注册使用）
     *
     * @param roleKey 角色权限字符串
     * @return 角色，不存在返回 null
     */
    @Override
    public SysRole selectRoleByKey(String roleKey)
    {
        return roleMapper.selectRoleByKey(roleKey);
    }

    /**
     * 根据用户ID获取角色选择框列表
     *
     * @param userId 用户ID
     * @return 选中角色ID列表
     */
    @Override
    public List<Long> selectRoleListByUserId(Long userId)
    {
        return roleMapper.selectRoleListByUserId(userId);
    }

    /**
     * 通过角色ID查询角色
     *
     * @param roleId 角色ID
     * @return 角色对象信息
     */
    @Override
    public SysRole selectRoleById(Long roleId)
    {
        return roleMapper.selectRoleById(roleId);
    }

    /**
     * 校验角色名称是否唯一
     *
     * @param role 角色信息
     * @return 结果
     */
    @Override
    public boolean checkRoleNameUnique(SysRole role)
    {
        Long roleId = StringUtils.isNull(role.getRoleId()) ? -1L : role.getRoleId();
        SysRole info = roleMapper.checkRoleNameUnique(role.getRoleName());
        if (StringUtils.isNotNull(info) && info.getRoleId().longValue() != roleId.longValue())
        {
            return UserConstants.NOT_UNIQUE;
        }
        return UserConstants.UNIQUE;
    }

    /**
     * 校验角色权限是否唯一
     *
     * @param role 角色信息
     * @return 结果
     */
    @Override
    public boolean checkRoleKeyUnique(SysRole role)
    {
        Long roleId = StringUtils.isNull(role.getRoleId()) ? -1L : role.getRoleId();
        SysRole info = roleMapper.checkRoleKeyUnique(role.getRoleKey());
        if (StringUtils.isNotNull(info) && info.getRoleId().longValue() != roleId.longValue())
        {
            return UserConstants.NOT_UNIQUE;
        }
        return UserConstants.UNIQUE;
    }

    /**
     * 校验角色是否允许操作
     *
     * @param role 角色信息
     */
    @Override
    public void checkRoleAllowed(SysRole role)
    {
        if (StringUtils.isNotNull(role.getRoleId()) && role.isAdmin())
        {
            throw new ServiceException("不允许操作超级管理员角色");
        }
    }

    /**
     * 校验角色是否有数据权限
     *
     * <p>
     * 原实现借用 {@code selectRoleList} 的 {@code @DataScope} 过滤，而该过滤拼接的是
     * {@code d.dept_id}（部门表），可 {@code sys_role} 本身没有 {@code dept_id}：
     * 过滤条件经由 {@code sys_user_role → sys_user → sys_dept} 联表后落在「持有该角色的用户」上，
     * 只要本家庭存在任意用户，join 就能命中，于是**任何角色都能通过校验**。
     * 这正是本次渗透中攻击者能新建 {@code pentest_admin} 并挂满 166 个菜单的根因。
     * </p>
     *
     * <p>
     * 改为按「角色是否可用于当前登录者的数据范围」显式判定，不再依赖联表：
     * 平台管理员放行；家庭侧只能操作三个家庭角色；其余一律拒绝。
     * 用角色归属而非部门归属，是因为 {@code sys_role} 没有部门字段，而家庭场景的角色集合是固定的。
     * </p>
     *
     * @param roleIds 角色id
     */
    @Override
    public void checkRoleDataScope(Long... roleIds)
    {
        if (SecurityUtils.isAdmin() || StringUtils.isEmpty(roleIds))
        {
            return;
        }
        for (Long roleId : roleIds)
        {
            if (roleId == null)
            {
                continue;
            }
            SysRole role = roleMapper.selectRoleById(roleId);
            // 角色不存在、已删除，或不属于家庭角色集合，一律视为越权
            if (role == null || !"0".equals(role.getDelFlag()) || !FamilyRoleConstants.isFamilyRole(role.getRoleKey()))
            {
                throw new ServiceException("没有权限访问角色数据！");
            }
        }
    }

    /**
     * 校验待分配角色是否可由家庭管理员授予
     *
     * <p>非平台管理员只能分配三个家庭角色，防止家庭管理员借用户管理越权授予
     * 平台级角色。平台管理员不受限。</p>
     *
     * @param roleIds 角色id
     */
    @Override
    public void checkFamilyRoleAssignable(Long... roleIds)
    {
        if (SecurityUtils.isAdmin() || StringUtils.isEmpty(roleIds))
        {
            return;
        }
        for (Long roleId : roleIds)
        {
            if (roleId == null)
            {
                continue;
            }
            SysRole role = roleMapper.selectRoleById(roleId);
            if (role == null || !"0".equals(role.getDelFlag()) || !FamilyRoleConstants.isFamilyRole(role.getRoleKey()))
            {
                throw new ServiceException("只能分配家庭角色（点餐员 / 厨师 / 家庭管理员）");
            }
        }
    }

    /**
     * 通过角色ID查询角色使用数量
     *
     * @param roleId 角色ID
     * @return 结果
     */
    @Override
    public int countUserRoleByRoleId(Long roleId)
    {
        return userRoleMapper.countUserRoleByRoleId(roleId);
    }

    /**
     * 新增保存角色信息
     *
     * @param role 角色信息
     * @return 结果
     */
    @Override
    @Transactional
    public int insertRole(SysRole role)
    {
        assertPlatformAdmin("新增角色");
        // 新增角色信息
        roleMapper.insertRole(role);
        return insertRoleMenu(role);
    }

    /**
     * 修改保存角色信息
     *
     * @param role 角色信息
     * @return 结果
     */
    @Override
    @Transactional
    public int updateRole(SysRole role)
    {
        assertPlatformAdmin("修改角色");
        // 修改角色信息
        roleMapper.updateRole(role);
        // 删除角色与菜单关联
        roleMenuMapper.deleteRoleMenuByRoleId(role.getRoleId());
        return insertRoleMenu(role);
    }

    /**
     * 修改角色状态
     *
     * @param role 角色信息
     * @return 结果
     */
    @Override
    public int updateRoleStatus(SysRole role)
    {
        assertPlatformAdmin("修改角色状态");
        return roleMapper.updateRole(role);
    }

    /**
     * 修改数据权限信息
     *
     * @param role 角色信息
     * @return 结果
     */
    @Override
    @Transactional
    public int authDataScope(SysRole role)
    {
        assertPlatformAdmin("修改角色数据权限");
        // 修改角色信息
        roleMapper.updateRole(role);
        // 删除角色与部门关联
        roleDeptMapper.deleteRoleDeptByRoleId(role.getRoleId());
        // 新增角色和部门信息（数据权限）
        return insertRoleDept(role);
    }

    /**
     * 新增角色菜单信息
     *
     * @param role 角色对象
     */
    public int insertRoleMenu(SysRole role)
    {
        int rows = 1;
        // 未勾选任何菜单时 menuIds 为 null，原实现会直接 NPE
        if (StringUtils.isEmpty(role.getMenuIds()))
        {
            return rows;
        }
        // 新增用户与角色管理
        List<SysRoleMenu> list = new ArrayList<SysRoleMenu>();
        for (Long menuId : role.getMenuIds())
        {
            SysRoleMenu rm = new SysRoleMenu();
            rm.setRoleId(role.getRoleId());
            rm.setMenuId(menuId);
            list.add(rm);
        }
        if (list.size() > 0)
        {
            rows = roleMenuMapper.batchRoleMenu(list);
        }
        return rows;
    }

    /**
     * 新增角色部门信息(数据权限)
     *
     * @param role 角色对象
     */
    public int insertRoleDept(SysRole role)
    {
        int rows = 1;
        // 新增角色与部门（数据权限）管理
        List<SysRoleDept> list = new ArrayList<SysRoleDept>();
        for (Long deptId : role.getDeptIds())
        {
            SysRoleDept rd = new SysRoleDept();
            rd.setRoleId(role.getRoleId());
            rd.setDeptId(deptId);
            list.add(rd);
        }
        if (list.size() > 0)
        {
            rows = roleDeptMapper.batchRoleDept(list);
        }
        return rows;
    }

    /**
     * 通过角色ID删除角色
     *
     * @param roleId 角色ID
     * @return 结果
     */
    @Override
    @Transactional
    public int deleteRoleById(Long roleId)
    {
        // 删除角色与菜单关联
        roleMenuMapper.deleteRoleMenuByRoleId(roleId);
        // 删除角色与部门关联
        roleDeptMapper.deleteRoleDeptByRoleId(roleId);
        return roleMapper.deleteRoleById(roleId);
    }

    /**
     * 批量删除角色信息
     *
     * @param roleIds 需要删除的角色ID
     * @return 结果
     */
    @Override
    @Transactional
    public int deleteRoleByIds(Long[] roleIds)
    {
        assertPlatformAdmin("删除角色");
        for (Long roleId : roleIds)
        {
            checkRoleAllowed(new SysRole(roleId));
            checkRoleDataScope(roleId);
            SysRole role = selectRoleById(roleId);
            if (countUserRoleByRoleId(roleId) > 0)
            {
                throw new ServiceException(String.format("%1$s已分配,不能删除", role.getRoleName()));
            }
        }
        // 删除角色与菜单关联
        roleMenuMapper.deleteRoleMenu(roleIds);
        // 删除角色与部门关联
        roleDeptMapper.deleteRoleDept(roleIds);
        return roleMapper.deleteRoleByIds(roleIds);
    }

    /**
     * 校验调用者是平台管理员
     *
     * <p>
     * 角色本身是平台级资源：家庭管理员只该管理「家庭成员及其家庭角色」，
     * 不该新建角色、改角色权限或改数据范围——否则可自建一个挂满菜单的角色再授予自己，
     * 正是本次渗透的提权路径。这里在服务层兜底，避免将来新增入口时漏加注解。
     * </p>
     *
     * @param action 动作名称，用于提示语
     */
    private void assertPlatformAdmin(String action)
    {
        if (!SecurityUtils.isAdmin())
        {
            throw new ServiceException("只有平台管理员可以" + action);
        }
    }

    /**
     * 取消授权用户角色
     *
     * @param userRole 用户和角色关联信息
     * @return 结果
     */
    @Override
    public int deleteAuthUser(SysUserRole userRole)
    {
        checkRoleDataScope(userRole.getRoleId());
        return userRoleMapper.deleteUserRoleInfo(userRole);
    }

    /**
     * 批量取消授权用户角色
     *
     * @param roleId 角色ID
     * @param userIds 需要取消授权的用户数据ID
     * @return 结果
     */
    @Override
    public int deleteAuthUsers(Long roleId, Long[] userIds)
    {
        checkRoleDataScope(roleId);
        return userRoleMapper.deleteUserRoleInfos(roleId, userIds);
    }

    /**
     * 批量选择授权用户角色
     *
     * @param roleId 角色ID
     * @param userIds 需要授权的用户数据ID
     * @return 结果
     */
    @Override
    public int insertAuthUsers(Long roleId, Long[] userIds)
    {
        checkRoleDataScope(roleId);
        checkFamilyRoleAssignable(roleId);
        // 新增用户与角色管理
        List<SysUserRole> list = new ArrayList<SysUserRole>();
        for (Long userId : userIds)
        {
            SysUserRole ur = new SysUserRole();
            ur.setUserId(userId);
            ur.setRoleId(roleId);
            list.add(ur);
        }
        return userRoleMapper.batchUserRole(list);
    }
}
