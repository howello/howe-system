package com.howe.common.dto.role;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * <p>@Author lu
 * <p>@Date 2022/12/13 17:31 星期二
 * <p>@Version 1.0
 * <p>@Description 角色和菜单关联表
 */
@ApiModel(value = "角色和菜单关联表")
@Schema
@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName(value = "sys_role_menu")
public class RoleMenuDTO implements Serializable {
    public RoleMenuDTO(String roleId) {
        this.roleId = roleId;
    }

    /**
     * 角色ID
     */
    @TableId(value = "role_id", type = IdType.INPUT)
    @ApiModelProperty(value = "角色ID")
    @Schema(description = "角色ID")
    private String roleId;

    /**
     * 菜单ID
     */
    @TableField(value = "menu_id")
    @ApiModelProperty(value = "菜单ID")
    @Schema(description = "菜单ID")
    private String menuId;

    private static final long serialVersionUID = 1L;

    public static final String COL_ROLE_ID = "role_id";

    public static final String COL_MENU_ID = "menu_id";
}
