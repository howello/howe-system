package com.howe.common.core.page;

import java.io.Serializable;
import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 表格分页数据对象
 *
 * @author howe
 */
@Schema(description = "表格分页数据对象")
public class TableDataInfo implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 总记录数 */
    @Schema(description = "总记录数", example = "100")
    private long total;

    /** 列表数据 */
    @Schema(description = "当前页列表数据")
    private List<?> rows;

    /** 消息状态码 */
    @Schema(description = "消息状态码，200 成功、500 失败", example = "200")
    private int code;

    /** 消息内容 */
    @Schema(description = "消息内容", example = "查询成功")
    private String msg;

    /**
     * 表格数据对象
     */
    public TableDataInfo()
    {
    }

    /**
     * 分页
     *
     * @param list 列表数据
     * @param total 总记录数
     */
    public TableDataInfo(List<?> list, long total)
    {
        this.rows = list;
        this.total = total;
    }

    public long getTotal()
    {
        return total;
    }

    public void setTotal(long total)
    {
        this.total = total;
    }

    public List<?> getRows()
    {
        return rows;
    }

    public void setRows(List<?> rows)
    {
        this.rows = rows;
    }

    public int getCode()
    {
        return code;
    }

    public void setCode(int code)
    {
        this.code = code;
    }

    public String getMsg()
    {
        return msg;
    }

    public void setMsg(String msg)
    {
        this.msg = msg;
    }
}
