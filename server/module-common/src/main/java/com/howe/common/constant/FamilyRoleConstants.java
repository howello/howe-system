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
     * 判断是否为家庭角色
     *
     * @param roleKey 角色权限字符串
     * @return 是家庭角色返回 true
     */
    public static boolean isFamilyRole(String roleKey)
    {
        return roleKey != null && FAMILY_ROLE_KEYS.contains(roleKey);
    }
}
