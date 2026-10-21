package com.howe.common.constant;

import java.util.Set;

/**
 * 家庭角色常量
 *
 * <p>家庭点餐系统里「家庭 = 部门、家庭成员 = 用户」，家庭侧只有三个角色。
 * 家庭管理员只能在这三个角色之间给成员授权，不能分配平台级角色。</p>
 *
 * @author howe
 */
public class FamilyRoleConstants
{
    /** 点餐员：家庭成员默认角色 */
    public static final String ROLE_EATER = "meal_eater";

    /** 厨师 */
    public static final String ROLE_CHEF = "meal_chef";

    /** 家庭管理员 */
    public static final String ROLE_MANAGER = "meal_manager";

    /** 全部家庭角色标识 */
    public static final Set<String> FAMILY_ROLE_KEYS = Set.of(ROLE_EATER, ROLE_CHEF, ROLE_MANAGER);

    /**
     * 允许登录管理端的角色标识
     *
     * <p>
     * 管理端（admin-ui）只给两类人用：管理员与家庭管理员。
     * 点餐员、厨师以及任何其他普通角色只能登录点餐端——否则他们能进到管理端界面，
     * 即便按钮受权限控制，菜单、部门树、用户列表这些页面骨架本身也是信息泄露面。
     * </p>
     *
     * <p>
     * 超级管理员（{@code userId == 1}）不靠角色判定，在代码里单独放行。
     * 这里的 {@code admin} 是平台管理员角色，{@code meal_manager} 是家庭管理员角色。
     * </p>
     */
    public static final Set<String> ADMIN_CONSOLE_ROLE_KEYS = Set.of("admin", ROLE_MANAGER);

    /**
     * 判断是否为家庭角色
     *
     * @param roleKey 角色权限字符串
     * @return 是家庭角色返回 true
     */
    public static boolean isFamilyRole(String roleKey)
    {
        return roleKey != null && FAMILY_ROLE_KEYS.contains(roleKey);
    }

    /**
     * 判断该角色是否允许登录管理端
     *
     * @param roleKey 角色权限字符串
     * @return 允许返回 true
     */
    public static boolean canAccessAdminConsole(String roleKey)
    {
        return roleKey != null && ADMIN_CONSOLE_ROLE_KEYS.contains(roleKey);
    }
}
