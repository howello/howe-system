package com.howe.meal.service;

import com.howe.common.core.domain.entity.SysRole;
import com.howe.common.core.domain.entity.SysUser;
import com.howe.meal.domain.dto.MealMemberBody;

import java.util.List;

/**
 * 点餐-家庭成员 服务层
 *
 * <p>家庭管理员在手机端维护本家庭成员。所有方法都只允许家庭管理员（或平台管理员）
 * 调用，且按登录用户的家庭做隔离：不能操作其他家庭的成员、平台超管，也不能让家庭
 * 失去最后一名家庭管理员。</p>
 *
 * @author howe
 */
public interface IMealMemberService {

    /**
     * 查询本家庭成员（含已分配的家庭角色）
     *
     * @param keyword 账号或昵称关键字，可为空
     * @return 成员集合
     */
    List<SysUser> selectFamilyMemberList(String keyword);

    /**
     * 查询可分配的家庭角色（点餐员 / 厨师 / 家庭管理员）
     *
     * @return 家庭角色集合
     */
    List<SysRole> selectAssignableRoles();

    /**
     * 新增本家庭成员
     *
     * @param body 成员信息（账号、昵称、初始密码、手机号、家庭角色）
     */
    void addMember(MealMemberBody body);

    /**
     * 修改本家庭成员（昵称、手机号、家庭角色）
     *
     * @param body 成员信息，userId 必填
     */
    void updateMember(MealMemberBody body);

    /**
     * 停用/启用本家庭成员
     *
     * @param userId 成员ID
     * @param status 账号状态（0正常 1停用）
     */
    void updateMemberStatus(Long userId, String status);

    /**
     * 移除本家庭成员（逻辑删除）
     *
     * @param userId 成员ID
     */
    void removeMember(Long userId);
}
