package com.howe.system.domain;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 角色和部门关联 sys_role_dept
 *
 * @author howe
 */
@Schema(description = "角色和部门关联")
public class SysRoleDept
{
    /** 角色ID */
    @Schema(description = "角色ID", example = "1")
    private Long roleId;

    /** 部门ID */
    @Schema(description = "部门ID", example = "100")
    private Long deptId;

    public Long getRoleId()
    {
        return roleId;
    }

    public void setRoleId(Long roleId)
    {
        this.roleId = roleId;
    }

    public Long getDeptId()
    {
        return deptId;
    }

    public void setDeptId(Long deptId)
    {
        this.deptId = deptId;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this,ToStringStyle.MULTI_LINE_STYLE)
            .append("roleId", getRoleId())
            .append("deptId", getDeptId())
            .toString();
    }
}
