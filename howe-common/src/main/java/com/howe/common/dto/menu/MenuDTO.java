package com.howe.common.dto.menu;

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

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;

/**
 * <p>@Author lu
 * <p>@Date 2022/11/29 14:19 星期二
 * <p>@Version 1.0
 * <p>@Description 菜单权限表
 */
@Schema
@Data
@EqualsAndHashCode(callSuper = true)
@AllArgsConstructor
@NoArgsConstructor
@TableName(value = "sys_menu")
@ApiModel("菜单")
public class MenuDTO extends BaseDTO {
    /**
     * 菜单ID
     */
    @TableId(value = "menu_id", type = IdType.INPUT)
    @Schema(description = "菜单ID")
    @ApiModelProperty("菜单ID")
    private Long menuId;

    /**
     * 菜单名称
     */
    @TableField(value = "menu_name")
    @Schema(description = "菜单名称")
    @ApiModelProperty("菜单名称")
    @NotBlank(message = "菜单名称不能为空")
    @Size(max = 50, message = "菜单名称长度不能超过50个字符")
    private String menuName;

    /**
     * 父菜单ID
     */
    @TableField(value = "parent_id")
    @Schema(description = "父菜单ID")
    @ApiModelProperty("父菜单ID")
    private Long parentId;

    /**
     * 显示顺序
     */
    @TableField(value = "order_num")
    @Schema(description = "显示顺序")
    @ApiModelProperty("显示顺序")
    @NotBlank(message = "显示顺序不能为空")
    private Integer orderNum;

    /**
     * 路由地址
     */
    @TableField(value = "`path`")
    @Schema(description = "路由地址")
    @ApiModelProperty("路由地址")
    @Size(max = 200, message = "路由地址不能超过200个字符")
    private String path;


    /**
     * 路由参数
     */
    @TableField(value = "query")
    @Schema(description = "路由参数")
    @ApiModelProperty("路由参数")
    private String query;

    /**
     * 是否为外链（0是 1否）
     */
    @TableField(value = "is_frame")
    @Schema(description = "是否为外链（0是 1否）")
    @ApiModelProperty("是否为外链（0是 1否）")
    private Integer isFrame;

    /**
     * 是否缓存（0缓存 1不缓存）
     */
    @TableField(value = "is_cache")
    @Schema(description = "是否缓存（0缓存 1不缓存）")
    @ApiModelProperty("是否缓存（0缓存 1不缓存）")
    private Integer isCache;

    /**
     * 菜单类型（M目录 C菜单 F按钮）
     */
    @TableField(value = "menu_type")
    @Schema(description = "菜单类型（M目录 C菜单 F按钮）")
    @ApiModelProperty("菜单类型（M目录 C菜单 F按钮）")
    @NotBlank(message = "菜单类型不能为空")
    private String menuType;

    /**
     * 菜单状态（0显示 1隐藏）
     */
    @TableField(value = "visible")
    @Schema(description = "菜单状态（0显示 1隐藏）")
    @ApiModelProperty("菜单状态（0显示 1隐藏）")
    private String visible;

    /**
     * 菜单状态（0正常 1停用）
     */
    @TableField(value = "`status`")
    @Schema(description = "菜单状态（0正常 1停用）")
    @ApiModelProperty("菜单状态（0正常 1停用）")
    private String status;

    /**
     * 菜单图标
     */
    @TableField(value = "icon")
    @Schema(description = "菜单图标")
    @ApiModelProperty("菜单图标")
    private String icon;

    /**
     * 子菜单
     */
    private List<MenuDTO> children = new ArrayList<>();

    public static final String COL_MENU_ID = "menu_id";

    public static final String COL_MENU_NAME = "menu_name";

    public static final String COL_PARENT_ID = "parent_id";

    public static final String COL_ORDER_NUM = "order_num";

    public static final String COL_PATH = "path";

    public static final String COL_COMPONENT = "component";

    public static final String COL_QUERY = "query";

    public static final String COL_IS_FRAME = "is_frame";

    public static final String COL_IS_CACHE = "is_cache";

    public static final String COL_MENU_TYPE = "menu_type";

    public static final String COL_VISIBLE = "visible";

    public static final String COL_STATUS = "status";

    public static final String COL_PERMS = "perms";

    public static final String COL_ICON = "icon";

    public boolean isTopMenu() {
        return this.getParentId() == 0L;
    }
}
