package com.howe.system.domain;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 用户和角色关联 sys_user_role
 *
 * @author howe
 */
@Schema(description = "用户和角色关联")
public class SysUserRole
{
    /** 用户ID */
    @Schema(description = "用户ID", example = "1")
    private Long userId;

    /** 角色ID */
    @Schema(description = "角色ID", example = "1")
    private Long roleId;

    public Long getUserId()
    {
        return userId;
    }

    public void setUserId(Long userId)
    {
        this.userId = userId;
    }

    public Long getRoleId()
    {
        return roleId;
    }

    public void setRoleId(Long roleId)
    {
        this.roleId = roleId;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this,ToStringStyle.MULTI_LINE_STYLE)
            .append("userId", getUserId())
            .append("roleId", getRoleId())
            .toString();
    }
}
