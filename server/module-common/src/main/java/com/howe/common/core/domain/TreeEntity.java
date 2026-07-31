package com.howe.common.core.domain;

import java.util.ArrayList;
import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Tree基类
 *
 * @author howe
 */
@Schema(description = "Tree基类，为树形结构实体提供父级、排序与子节点字段")
public class TreeEntity extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 父菜单名称 */
    @Schema(description = "父菜单名称")
    private String parentName;

    /** 父菜单ID */
    @Schema(description = "父菜单ID", example = "0")
    private Long parentId;

    /** 显示顺序 */
    @Schema(description = "显示顺序", example = "1")
    private Integer orderNum;

    /** 祖级列表 */
    @Schema(description = "祖级列表，逗号分隔的所有上级ID", example = "0,100")
    private String ancestors;

    /** 子部门 */
    @Schema(description = "子部门")
    private List<?> children = new ArrayList<>();

    public String getParentName()
    {
        return parentName;
    }

    public void setParentName(String parentName)
    {
        this.parentName = parentName;
    }

    public Long getParentId()
    {
        return parentId;
    }

    public void setParentId(Long parentId)
    {
        this.parentId = parentId;
    }

    public Integer getOrderNum()
    {
        return orderNum;
    }

    public void setOrderNum(Integer orderNum)
    {
        this.orderNum = orderNum;
    }

    public String getAncestors()
    {
        return ancestors;
    }

    public void setAncestors(String ancestors)
    {
        this.ancestors = ancestors;
    }

    public List<?> getChildren()
    {
        return children;
    }

    public void setChildren(List<?> children)
    {
        this.children = children;
    }
}
