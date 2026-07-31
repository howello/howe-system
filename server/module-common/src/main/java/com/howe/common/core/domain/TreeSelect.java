package com.howe.common.core.domain;

import java.io.Serializable;
import java.util.List;
import java.util.stream.Collectors;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.howe.common.constant.UserConstants;
import com.howe.common.core.domain.entity.SysDept;
import com.howe.common.core.domain.entity.SysMenu;
import com.howe.common.utils.StringUtils;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Treeselect树结构实体类
 *
 * @author howe
 */
@Schema(description = "Treeselect树结构实体，用于前端树形下拉选择")
public class TreeSelect implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 节点ID */
    @Schema(description = "节点ID", example = "100")
    private Long id;

    /** 节点名称 */
    @Schema(description = "节点名称", example = "研发部门")
    private String label;

    /** 节点禁用 */
    @Schema(description = "节点是否禁用选择", example = "false")
    private boolean disabled = false;

    /** 子节点 */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @Schema(description = "子节点列表")
    private List<TreeSelect> children;

    public TreeSelect()
    {

    }

    public TreeSelect(SysDept dept)
    {
        this.id = dept.getDeptId();
        this.label = dept.getDeptName();
        this.disabled = StringUtils.equals(UserConstants.DEPT_DISABLE, dept.getStatus());
        this.children = dept.getChildren().stream().map(TreeSelect::new).collect(Collectors.toList());
    }

    public TreeSelect(SysMenu menu)
    {
        this.id = menu.getMenuId();
        this.label = menu.getMenuName();
        this.children = menu.getChildren().stream().map(TreeSelect::new).collect(Collectors.toList());
    }

    public Long getId()
    {
        return id;
    }

    public void setId(Long id)
    {
        this.id = id;
    }

    public String getLabel()
    {
        return label;
    }

    public void setLabel(String label)
    {
        this.label = label;
    }

    public boolean isDisabled()
    {
        return disabled;
    }

    public void setDisabled(boolean disabled)
    {
        this.disabled = disabled;
    }

    public List<TreeSelect> getChildren()
    {
        return children;
    }

    public void setChildren(List<TreeSelect> children)
    {
        this.children = children;
    }
}
