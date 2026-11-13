package com.howe.common.tool.model;

import java.util.Set;

/**
 * 工具的业务访问要求。
 *
 * <p>三个条件组之间为 AND：{@code allOf} 必须全部满足，{@code anyOf} 至少满足一项，
 * {@code roles} 至少匹配一个角色键；{@code authenticatedOnly} 表示只要求已认证账号。
 * 旧工具的 {@code permissions} 集合通过 {@link #ofPermissions(Set)} 转换为 {@code allOf}，
 * 继续保留原有 AND 语义。</p>
 *
 * <p>本类型只表达业务权限与角色，不承载 OAuth scope、工具开关或授权状态。</p>
 *
 * @param allOf 必须全部具备的权限串
 * @param anyOf 至少具备一项的权限串
 * @param roles 至少匹配一个的角色键
 * @param authenticatedOnly 是否仅要求已认证账号
 */
public record ToolAccess(Set<String> allOf, Set<String> anyOf, Set<String> roles, boolean authenticatedOnly) {

    /** 防御性复制集合，避免调用方后续修改影响已登记工具。 */
    public ToolAccess {
        allOf = allOf == null ? Set.of() : Set.copyOf(allOf);
        anyOf = anyOf == null ? Set.of() : Set.copyOf(anyOf);
        roles = roles == null ? Set.of() : Set.copyOf(roles);
    }

    /**
     * 由权限集合构造 AND 策略。
     *
     * @param permissions 原有权限集合
     * @return 兼容旧语义的访问策略
     */
    public static ToolAccess ofPermissions(Set<String> permissions) {
        return new ToolAccess(permissions, Set.of(), Set.of(), false);
    }

    /**
     * 构造仅要求已认证账号的策略。
     *
     * @return 仅登录策略
     */
    public static ToolAccess authenticated() {
        return new ToolAccess(Set.of(), Set.of(), Set.of(), true);
    }

    /**
     * 构造仅要求角色匹配的策略。
     *
     * <p>内置平台账号按 {@code platformAdmin} 语义豁免角色键，但业务守卫仍按原方法执行；
     * 普通账号即使拥有同名角色键也不会跳过 scope、工具开关或数据归属。</p>
     *
     * @param roles 至少匹配一个的角色键
     * @return 角色策略
     */
    public static ToolAccess ofRoles(Set<String> roles) {
        return new ToolAccess(Set.of(), Set.of(), roles, false);
    }

    /**
     * 构造不附加业务条件的空策略，仅用于平台内部工具。
     *
     * @return 空策略
     */
    public static ToolAccess empty() {
        return new ToolAccess(Set.of(), Set.of(), Set.of(), false);
    }

    /**
     * 判断是否没有任何显式业务条件。
     *
     * @return 无业务权限、无角色且非仅登录时返回 true
     */
    public boolean emptyAccess() {
        return allOf.isEmpty() && anyOf.isEmpty() && roles.isEmpty() && !authenticatedOnly;
    }

    /**
     * 按当前账号权限与角色判定是否放行。
     *
     * <p>{@code *:*:*} 只豁免权限集合，不绕过 scope、工具开关、授权状态或业务归属。
     * {@code platformAdmin} 表示内置平台账号语义，与"拥有 admin 角色键"不是同一概念。</p>
     *
     * <p>{@code authenticatedOnly} 要求调用方显式传入已认证信号：{@code permissions} 与
     * {@code roleKeys} 都为 {@code null} 时视为未认证并拒绝。业务调用方应传入非 null 集合。</p>
     *
     * @param permissions 当前账号权限集合，可为空集合
     * @param roleKeys 当前账号角色键集合，可为空集合
     * @param platformAdmin 是否为内置平台账号
     * @return 全部条件组均满足时返回 true
     */
    public boolean allows(Set<String> permissions, Set<String> roleKeys, boolean platformAdmin) {
        boolean authenticated = permissions != null || roleKeys != null;
        if (!authenticated) {
            return false;
        }
        Set<String> granted = permissions == null ? Set.of() : permissions;
        Set<String> rolesOwned = roleKeys == null ? Set.of() : roleKeys;
        boolean allPermission = granted.contains("*:*:*");
        if (!allPermission && !granted.containsAll(allOf)) {
            return false;
        }
        if (!anyOf.isEmpty() && !allPermission && anyOf.stream().noneMatch(granted::contains)) {
            return false;
        }
        if (!roles.isEmpty() && !platformAdmin && rolesOwned.stream().noneMatch(roles::contains)) {
            return false;
        }
        return true;
    }
}
