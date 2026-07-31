package com.howe.common.core.page;

import com.howe.common.utils.StringUtils;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 分页数据
 *
 * @author howe
 */
@Schema(description = "分页查询参数")
public class PageDomain
{
    /** 当前记录起始索引 */
    @Schema(description = "当前页码，从 1 开始", example = "1")
    private Integer pageNum;

    /** 每页显示记录数 */
    @Schema(description = "每页显示记录数", example = "10")
    private Integer pageSize;

    /** 排序列 */
    @Schema(description = "排序列（驼峰字段名，后端转下划线）", example = "createTime")
    private String orderByColumn;

    /** 排序的方向desc或者asc */
    @Schema(description = "排序方向，asc 或 desc（兼容前端的 ascending/descending）", example = "asc")
    private String isAsc = "asc";

    /** 分页参数合理化 */
    @Schema(description = "分页参数合理化，页码越界时自动修正", example = "true")
    private Boolean reasonable = true;

    public String getOrderBy()
    {
        if (StringUtils.isEmpty(orderByColumn))
        {
            return "";
        }
        return StringUtils.toUnderScoreCase(orderByColumn) + " " + isAsc;
    }

    public Integer getPageNum()
    {
        return pageNum;
    }

    public void setPageNum(Integer pageNum)
    {
        this.pageNum = pageNum;
    }

    public Integer getPageSize()
    {
        return pageSize;
    }

    public void setPageSize(Integer pageSize)
    {
        this.pageSize = pageSize;
    }

    public String getOrderByColumn()
    {
        return orderByColumn;
    }

    public void setOrderByColumn(String orderByColumn)
    {
        this.orderByColumn = orderByColumn;
    }

    public String getIsAsc()
    {
        return isAsc;
    }

    public void setIsAsc(String isAsc)
    {
        if (StringUtils.isNotEmpty(isAsc))
        {
            // 兼容前端排序类型
            if ("ascending".equals(isAsc))
            {
                isAsc = "asc";
            }
            else if ("descending".equals(isAsc))
            {
                isAsc = "desc";
            }
            this.isAsc = isAsc;
        }
    }

    public Boolean getReasonable()
    {
        if (StringUtils.isNull(reasonable))
        {
            return Boolean.TRUE;
        }
        return reasonable;
    }

    public void setReasonable(Boolean reasonable)
    {
        this.reasonable = reasonable;
    }
}
