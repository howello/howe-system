package com.howe.meal.service.impl;

import com.howe.common.constant.FamilyRoleConstants;
import com.howe.common.constant.UserConstants;
import com.howe.common.core.domain.entity.SysRole;
import com.howe.common.core.domain.entity.SysUser;
import com.howe.common.exception.ServiceException;
import com.howe.common.utils.DateUtils;
import com.howe.common.utils.SecurityUtils;
import com.howe.common.utils.StringUtils;
import com.howe.meal.domain.dto.MealMemberBody;
import com.howe.meal.service.IMealMemberService;
import com.howe.system.mapper.SysUserMapper;
import com.howe.system.service.ISysRoleService;
import com.howe.system.service.ISysUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 点餐-家庭成员 服务层实现
 *
 * @author howe
 */
@Service
@RequiredArgsConstructor
public class MealMemberServiceImpl implements IMealMemberService {

    /** 账号状态：正常 */
    private static final String STATUS_NORMAL = "0";

    /** 账号状态：停用 */
    private static final String STATUS_DISABLED = "1";

    private final SysUserMapper userMapper;
    private final ISysUserService userService;
    private final ISysRoleService roleService;

    @Override
    public List<SysUser> selectFamilyMemberList(String keyword) {
        assertFamilyManager();
        return userMapper.selectFamilyMemberList(scopedDeptId(), keyword);
    }

    @Override
    public List<SysRole> selectAssignableRoles() {
        assertFamilyManager();
        return List.of(FamilyRoleConstants.ROLE_EATER, FamilyRoleConstants.ROLE_CHEF, FamilyRoleConstants.ROLE_MANAGER)
                .stream()
                .map(roleService::selectRoleByKey)
                .filter(role -> role != null && "0".equals(role.getStatus()))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void addMember(MealMemberBody body) {
        assertFamilyManager();
        Long deptId = scopedDeptId();
        if (deptId == null) {
            throw new ServiceException("当前账号未归属任何家庭，无法新增成员");
        }

        String userName = body.getUserName();
        String password = body.getPassword();
        String nickName = body.getNickName();
        String phonenumber = body.getPhonenumber();

        if (StringUtils.isEmpty(userName)) {
            throw new ServiceException("登录账号不能为空");
        }
        if (userName.length() < UserConstants.USERNAME_MIN_LENGTH
                || userName.length() > UserConstants.USERNAME_MAX_LENGTH) {
            throw new ServiceException("账号长度必须在2到20个字符之间");
        }
        if (StringUtils.isEmpty(password)) {
            throw new ServiceException("初始密码不能为空");
        }
        if (password.length() < UserConstants.PASSWORD_MIN_LENGTH
                || password.length() > UserConstants.PASSWORD_MAX_LENGTH) {
            throw new ServiceException("密码长度必须在5到20个字符之间");
        }
        if (StringUtils.isEmpty(nickName)) {
            throw new ServiceException("昵称不能为空");
        }
        if (nickName.length() > 30) {
            throw new ServiceException("昵称长度不能超过30个字符");
        }
        if (StringUtils.isNotEmpty(phonenumber) && phonenumber.length() > 11) {
            throw new ServiceException("手机号码长度不能超过11个字符");
        }

        SysUser probe = new SysUser();
        probe.setUserName(userName);
        probe.setPhonenumber(phonenumber);
        if (!userService.checkUserNameUnique(probe)) {
            throw new ServiceException("登录账号'" + userName + "'已存在");
        }
        if (StringUtils.isNotEmpty(phonenumber) && !userService.checkPhoneUnique(probe)) {
            throw new ServiceException("手机号码已存在");
        }

        SysUser member = new SysUser();
        member.setUserName(userName);
        member.setNickName(nickName);
        member.setPhonenumber(phonenumber);
        member.setDeptId(deptId);
        member.setStatus(STATUS_NORMAL);
        member.setCreateBy(SecurityUtils.getUsername());
        member.setPwdUpdateDate(DateUtils.getNowDate());
        member.setPassword(SecurityUtils.encryptPassword(password));
        member.setRoleIds(normalizeFamilyRoleIds(body.getRoleIds()));
        if (userService.insertUser(member) <= 0) {
            throw new ServiceException("新增成员失败");
        }
    }

    @Override
    @Transactional
    public void updateMember(MealMemberBody body) {
        assertFamilyManager();
        // 允许编辑自己（改昵称/手机号），但下面会单独拦住「去掉自己的家庭管理员角色」
        SysUser member = requireManageableMember(body.getUserId(), true);

        String nickName = body.getNickName();
        String phonenumber = body.getPhonenumber();
        if (StringUtils.isEmpty(nickName)) {
            throw new ServiceException("昵称不能为空");
        }
        if (nickName.length() > 30) {
            throw new ServiceException("昵称长度不能超过30个字符");
        }
        if (StringUtils.isNotEmpty(phonenumber) && phonenumber.length() > 11) {
            throw new ServiceException("手机号码长度不能超过11个字符");
        }

        SysUser probe = new SysUser();
        probe.setUserId(member.getUserId());
        probe.setUserName(member.getUserName());
        probe.setPhonenumber(phonenumber);
        if (StringUtils.isNotEmpty(phonenumber) && !userService.checkPhoneUnique(probe)) {
            throw new ServiceException("手机号码已存在");
        }

        Long[] roleIds = normalizeFamilyRoleIds(body.getRoleIds());
        boolean remainsManager = hasManagerRole(roleIds);
        if (Objects.equals(member.getUserId(), SecurityUtils.getUserId()) && !remainsManager) {
            throw new ServiceException("不能移除自己的家庭管理员角色");
        }
        boolean remainsActive = STATUS_NORMAL.equals(member.getStatus());
        assertFamilyKeepsManager(member.getDeptId(), member.getUserId(), remainsManager && remainsActive);

        SysUser update = new SysUser();
        update.setUserId(member.getUserId());
        update.setDeptId(member.getDeptId());
        update.setNickName(nickName);
        update.setPhonenumber(phonenumber);
        update.setUpdateBy(SecurityUtils.getUsername());
        update.setRoleIds(roleIds);
        if (userService.updateUser(update) <= 0) {
            throw new ServiceException("修改成员失败");
        }
    }

    @Override
    public void updateMemberStatus(Long userId, String status) {
        assertFamilyManager();
        if (!STATUS_NORMAL.equals(status) && !STATUS_DISABLED.equals(status)) {
            throw new ServiceException("账号状态不合法");
        }
        SysUser member = requireManageableMember(userId, false);
        if (STATUS_NORMAL.equals(member.getStatus()) && STATUS_DISABLED.equals(status)) {
            assertFamilyKeepsManager(member.getDeptId(), member.getUserId(), false);
        }
        SysUser update = new SysUser();
        update.setUserId(userId);
        update.setStatus(status);
        update.setUpdateBy(SecurityUtils.getUsername());
        if (userService.updateUserStatus(update) <= 0) {
            throw new ServiceException("修改成员状态失败");
        }
    }

    @Override
    @Transactional
    public void removeMember(Long userId) {
        assertFamilyManager();
        SysUser member = requireManageableMember(userId, false);
        assertFamilyKeepsManager(member.getDeptId(), member.getUserId(), false);
        if (userService.deleteUserByIds(new Long[] { userId }) <= 0) {
            throw new ServiceException("移除成员失败");
        }
    }

    /**
     * 校验调用者是家庭管理员（平台管理员天然放行）
     */
    private void assertFamilyManager() {
        if (!SecurityUtils.isAdmin() && !SecurityUtils.hasRole(FamilyRoleConstants.ROLE_MANAGER)) {
            throw new ServiceException("只有家庭管理员可以管理家庭成员");
        }
    }

    /**
     * 当前操作应作用于哪个家庭：平台管理员不过滤家庭，其余取登录用户的家庭
     *
     * @return 家庭（部门）ID；家庭账号未归属家庭时返回 null
     */
    private Long scopedDeptId() {
        if (SecurityUtils.isAdmin()) {
            return null;
        }
        Long deptId = SecurityUtils.getDeptId();
        if (deptId == null) {
            throw new ServiceException("当前账号未归属任何家庭");
        }
        return deptId;
    }

    /**
     * 取出可被当前调用者操作的成员，并完成越权校验
     *
     * @param userId    成员ID
     * @param allowSelf 是否允许操作自己；改资料允许，停用/移除不允许
     * @return 成员
     */
    private SysUser requireManageableMember(Long userId, boolean allowSelf) {
        if (userId == null) {
            throw new ServiceException("成员ID不能为空");
        }
        if (!allowSelf && Objects.equals(userId, SecurityUtils.getUserId())) {
            throw new ServiceException("不能对自己执行该操作");
        }
        SysUser member = userService.selectUserById(userId);
        if (member == null || !"0".equals(member.getDelFlag())) {
            throw new ServiceException("成员不存在");
        }
        if (member.isAdmin()) {
            throw new ServiceException("不允许操作平台超级管理员");
        }
        if (!SecurityUtils.isAdmin() && !Objects.equals(member.getDeptId(), SecurityUtils.getDeptId())) {
            throw new ServiceException("无权操作其他家庭的成员");
        }
        return member;
    }

    /**
     * 校验角色只包含家庭角色，未指定时默认点餐员
     *
     * @param roleIds 前端传入的角色ID
     * @return 规范化后的角色ID
     */
    private Long[] normalizeFamilyRoleIds(Long[] roleIds) {
        if (roleIds == null || roleIds.length == 0) {
            SysRole eater = findFamilyRole(FamilyRoleConstants.ROLE_EATER);
            if (eater == null) {
                throw new ServiceException("家庭默认角色未配置");
            }
            return new Long[] { eater.getRoleId() };
        }
        for (Long roleId : roleIds) {
            SysRole role = roleId == null ? null : roleService.selectRoleById(roleId);
            if (role == null || !FamilyRoleConstants.isFamilyRole(role.getRoleKey())) {
                throw new ServiceException("只能分配家庭角色（点餐员 / 厨师 / 家庭管理员）");
            }
        }
        return roleIds;
    }

    /**
     * 按角色标识查找家庭角色
     */
    private SysRole findFamilyRole(String roleKey) {
        SysRole role = roleService.selectRoleByKey(roleKey);
        return role != null && "0".equals(role.getStatus()) ? role : null;
    }

    /**
     * 判断一组角色ID里是否含家庭管理员
     */
    private boolean hasManagerRole(Long[] roleIds) {
        if (roleIds == null) {
            return false;
        }
        for (Long roleId : roleIds) {
            SysRole role = roleId == null ? null : roleService.selectRoleById(roleId);
            if (role != null && FamilyRoleConstants.ROLE_MANAGER.equals(role.getRoleKey())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 校验变更后家庭仍至少保留一名启用中的家庭管理员
     *
     * @param familyDeptId      目标成员所属家庭
     * @param targetUserId      被变更的成员
     * @param targetStillActive 该成员变更后是否仍是启用中的家庭管理员
     */
    private void assertFamilyKeepsManager(Long familyDeptId, Long targetUserId, boolean targetStillActive) {
        if (targetStillActive) {
            return;
        }
        List<SysUser> members = userMapper.selectFamilyMemberList(familyDeptId, null);
        boolean hasOtherManager = members.stream()
                .filter(member -> !Objects.equals(member.getUserId(), targetUserId))
                .anyMatch(member -> STATUS_NORMAL.equals(member.getStatus()) && hasManagerRole(member));
        if (!hasOtherManager) {
            throw new ServiceException("家庭至少需要保留一名家庭管理员");
        }
    }

    /**
     * 判断成员是否持有家庭管理员角色
     */
    private boolean hasManagerRole(SysUser member) {
        if (member.getRoles() == null) {
            return false;
        }
        return member.getRoles().stream()
                .anyMatch(role -> FamilyRoleConstants.ROLE_MANAGER.equals(role.getRoleKey()));
    }
}
