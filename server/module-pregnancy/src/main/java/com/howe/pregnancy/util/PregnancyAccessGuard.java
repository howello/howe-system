package com.howe.pregnancy.util;

import com.howe.common.core.domain.entity.SysRole;
import com.howe.common.utils.SecurityUtils;
import com.howe.pregnancy.constant.PregnancyConstants;
import com.howe.system.mapper.SysRoleMapper;
import com.howe.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 孕期访问守卫
 *
 * <p>目标模型没有档案与成员关系，访问资格由<b>现有系统账号状态</b>与
 * <b>当前有效的孕期角色</b>共同决定。所有孕期接口都经过这里，不各写一套判断。</p>
 *
 * <p>判定顺序：</p>
 * <ol>
 *   <li>取当前账号主键；无会话时按未认证处理；</li>
 *   <li>按主键回查 {@code sys_user}，要求存在、正常、未删除；</li>
 *   <li>按 {@code sys_user_role} 联查 {@code sys_role}，只保留孕期管理员且
 *       角色本身 {@code status='0'}、{@code del_flag='0'}；</li>
 *   <li>没有任何有效孕期角色时按「无读取资格」返回 404，不暴露业务是否存在；</li>
 *   <li>有读取资格但角色不允许写入或管理提醒时返回 403。</li>
 * </ol>
 *
 * <p><b>不能只依赖登录时的权限快照：</b>角色被撤销或账号被停用后，旧 token 仍可能通过
 * JWT 校验，因此这里每次请求都回查数据库。平台管理员身份不自动获得孕期业务权限。</p>
 *
 * @author howe
 */
@Component
@RequiredArgsConstructor
public class PregnancyAccessGuard {

    private final SysUserMapper sysUserMapper;

    private final SysRoleMapper sysRoleMapper;

    /**
     * 取当前登录账号主键
     *
     * @return 账号主键
     */
    public Long currentUserId() {
        try {
            return SecurityUtils.getUserId();
        } catch (RuntimeException e) {
            throw PregnancyException.unauthenticated();
        }
    }

    /**
     * 取当前账号的孕期角色键集合
     *
     * <p>每次调用都回查数据库，保证撤销角色、停用角色或停用账号即时生效。</p>
     *
     * @return 有效孕期角色键集合，可能为空
     */
    public Set<String> currentRoles() {
        Long userId = currentUserId();
        com.howe.common.core.domain.entity.SysUser user = sysUserMapper.selectUserById(userId);
        if (user == null || user.getUserId() == null || !"0".equals(user.getStatus())
                || !"0".equals(user.getDelFlag())) {
            throw PregnancyException.notFound();
        }
        List<SysRole> roles = sysRoleMapper.selectRolePermissionByUserId(userId);
        Set<String> result = new HashSet<>();
        if (roles == null) {
            return result;
        }
        for (SysRole role : roles) {
            if (role == null || role.getRoleKey() == null) {
                continue;
            }
            if (!PregnancyConstants.ALL_ROLES.contains(role.getRoleKey())) {
                continue;
            }
            if (!"0".equals(role.getStatus()) || !"0".equals(role.getDelFlag())) {
                continue;
            }
            result.add(role.getRoleKey());
        }
        return result;
    }

    /**
     * 要求当前账号具备孕期读取资格
     *
     * <p>孕期管理员有读取权限。</p>
     *
     * @return 有效角色集合
     */
    public Set<String> requireReadable() {
        Set<String> roles = currentRoles();
        if (roles.isEmpty()) {
            throw PregnancyException.notFound();
        }
        return roles;
    }

    /**
     * 要求当前账号具备孕期写入资格
     *
     * <p>需要先有读取资格；只有管理员与录入角色可写。</p>
     *
     * @return 有效角色集合
     */
    public Set<String> requireWritable() {
        Set<String> roles = requireReadable();
        if (roles.stream().noneMatch(PregnancyConstants.WRITABLE_ROLES::contains)) {
            throw PregnancyException.forbidden("编辑业务记录");
        }
        return roles;
    }

    /**
     * 要求当前账号是孕期管理员
     *
     * @return 有效角色集合
     */
    public Set<String> requireManager() {
        Set<String> roles = requireReadable();
        if (!roles.contains(PregnancyConstants.ROLE_MANAGER)) {
            throw PregnancyException.forbidden("管理提醒规则");
        }
        return roles;
    }

    /**
     * 判断是否可写（用于组装展示用能力，不做拦截）
     *
     * @param roles 有效角色集合
     * @return 任一可写角色存在时返回 true
     */
    public boolean canWrite(Set<String> roles) {
        return roles != null && roles.stream().anyMatch(PregnancyConstants.WRITABLE_ROLES::contains);
    }

    /**
     * 判断是否可管理提醒（用于组装展示用能力）
     *
     * @param roles 有效角色集合
     * @return 孕期管理员返回 true
     */
    public boolean canManageReminders(Set<String> roles) {
        return roles != null && roles.contains(PregnancyConstants.ROLE_MANAGER);
    }

    /**
     * 判断指定账号当前是否仍有孕期读取资格
     *
     * <p>后台任务在发布导出结果前要用申请人的账号重新核对，不能沿用任务创建时的快照。</p>
     *
     * @param userId 账号主键
     * @return 仍有资格返回 true
     */
    public boolean hasReadAccess(Long userId) {
        if (userId == null) {
            return false;
        }
        com.howe.common.core.domain.entity.SysUser user = sysUserMapper.selectUserById(userId);
        if (user == null || !"0".equals(user.getStatus()) || !"0".equals(user.getDelFlag())) {
            return false;
        }
        List<SysRole> roles = sysRoleMapper.selectRolePermissionByUserId(userId);
        if (roles == null) {
            return false;
        }
        return roles.stream().anyMatch(role -> role != null && role.getRoleKey() != null
                && PregnancyConstants.ALL_ROLES.contains(role.getRoleKey())
                && "0".equals(role.getStatus()) && "0".equals(role.getDelFlag()));
    }
}
