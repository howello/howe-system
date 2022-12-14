package com.howe.common.dto.role;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.howe.common.dto.BaseDTO;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.security.access.AccessDecisionManager;
import org.springframework.security.core.GrantedAuthority;

import java.util.List;

/**
 * <p>@Author lu
 * <p>@Date 2022/12/1 18:19 星期四
 * <p>@Version 1.0
 * <p>@Description 角色信息表
 */
@ApiModel(value = "角色信息表")
@Schema
@Data
@EqualsAndHashCode(callSuper = true)
@AllArgsConstructor
@NoArgsConstructor
@TableName(value = "sys_role")
public class RoleDTO extends BaseDTO implements GrantedAuthority {
    private static final long serialVersionUID = -6196154825918661330L;

    public RoleDTO(String roleId) {
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
     * 角色名称
     */
    @TableField(value = "role_name")
    @ApiModelProperty(value = "角色名称")
    @Schema(description = "角色名称")
    private String roleName;

    /**
     * 角色权限字符串
     */
    @TableField(value = "role_key")
    @ApiModelProperty(value = "角色权限字符串")
    @Schema(description = "角色权限字符串")
    private String roleKey;

    /**
     * 显示顺序
     */
    @TableField(value = "role_sort")
    @ApiModelProperty(value = "显示顺序")
    @Schema(description = "显示顺序")
    private Integer roleSort;

    /**
     * 数据范围（1：全部数据权限 2：自定数据权限 3：本部门数据权限 4：本部门及以下数据权限）
     */
    @TableField(value = "data_scope")
    @ApiModelProperty(value = "数据范围（1：全部数据权限 2：自定数据权限 3：本部门数据权限 4：本部门及以下数据权限）")
    @Schema(description = "数据范围（1：全部数据权限 2：自定数据权限 3：本部门数据权限 4：本部门及以下数据权限）")
    private String dataScope;

    /**
     * 菜单树选择项是否关联显示
     */
    @TableField(value = "menu_check_strictly")
    @ApiModelProperty(value = "菜单树选择项是否关联显示")
    @Schema(description = "菜单树选择项是否关联显示")
    private Boolean menuCheckStrictly;


    /**
     * 角色状态（0正常 1停用）
     */
    @TableField(value = "`status`")
    @ApiModelProperty(value = "角色状态（0正常 1停用）")
    @Schema(description = "角色状态（0正常 1停用）")
    private String status;

    /**
     * 删除标志（0代表存在 2代表删除）
     */
    @TableField(value = "del_flag")
    @ApiModelProperty(value = "删除标志（0代表存在 2代表删除）")
    @Schema(description = "删除标志（0代表存在 2代表删除）")
    private String delFlag;

    @TableField(exist = false)
    private List<String> menuIds;

    public static final String COL_ROLE_ID = "role_id";

    public static final String COL_ROLE_NAME = "role_name";

    public static final String COL_ROLE_KEY = "role_key";

    public static final String COL_ROLE_SORT = "role_sort";

    public static final String COL_DATA_SCOPE = "data_scope";

    public static final String COL_MENU_CHECK_STRICTLY = "menu_check_strictly";

    public static final String COL_DEPT_CHECK_STRICTLY = "dept_check_strictly";

    public static final String COL_STATUS = "status";

    public static final String COL_DEL_FLAG = "del_flag";

    /**
     * 如果 <code>GrantedAuthority<code> 可以表示为 <code>String<code> 并且 <code>String<code> 的精度足以依赖于 {@link AccessDecisionManager}
     * 的访问控制决策（或委托），此方法应返回这样一个 <code>String<code>。 <p> 如果 <code>GrantedAuthority<code> 不能以足够的精度表示为 <code>String<code>，则应返回 <code>null<code>。返回
     * <code>null<code> 将需要 <code>AccessDecisionManager<code>（或委托）专门支持 <code>GrantedAuthority<code> 实现，因此除非实际需要，否则应避免返回 <code>null<code> .
     *
     * @return a representation of the granted authority (or <code>null</code> if the
     * granted authority cannot be expressed as a <code>String</code> with sufficient
     * precision).
     */
    @Override
    public String getAuthority() {
        return roleKey;
    }
}
