package com.howe.system.domain.dto;

/**
 * 家庭成员工具投影：只含成员主表最小字段。
 *
 * @param userId 用户ID
 * @param userName 账号名
 * @param nickName 昵称
 * @param status 账号状态（0正常 1停用）
 */
public record FamilyMemberToolRow(Long userId, String userName, String nickName, String status) {
}
