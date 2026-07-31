package com.howe.system.domain;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.howe.common.annotation.Excel;
import com.howe.common.annotation.Excel.ColumnType;
import com.howe.common.core.domain.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 操作日志记录表 oper_log
 *
 * @author howe
 */
@Schema(description = "操作日志记录")
public class SysOperLog extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 日志主键 */
    @Excel(name = "操作序号", cellType = ColumnType.NUMERIC)
    @Schema(description = "日志主键", example = "1")
    private Long operId;

    /** 操作模块 */
    @Excel(name = "操作模块")
    @Schema(description = "操作模块", example = "用户管理")
    private String title;

    /** 业务类型（0其它 1新增 2修改 3删除） */
    @Excel(name = "业务类型", readConverterExp = "0=其它,1=新增,2=修改,3=删除,4=授权,5=导出,6=导入,7=强退,8=生成代码,9=清空数据")
    @Schema(description = "业务类型（0其它 1新增 2修改 3删除 4授权 5导出 6导入 7强退 8生成代码 9清空数据）", example = "1")
    private Integer businessType;

    /** 业务类型数组 */
    @Schema(description = "业务类型数组，用于按多个业务类型检索")
    private Integer[] businessTypes;

    /** 请求方法 */
    @Excel(name = "请求方法")
    @Schema(description = "请求方法", example = "com.howe.web.controller.system.SysUserController.add()")
    private String method;

    /** 请求方式 */
    @Excel(name = "请求方式")
    @Schema(description = "请求方式", example = "POST")
    private String requestMethod;

    /** 操作类别（0其它 1后台用户 2手机端用户） */
    @Excel(name = "操作类别", readConverterExp = "0=其它,1=后台用户,2=手机端用户")
    @Schema(description = "操作类别（0其它 1后台用户 2手机端用户）", example = "1")
    private Integer operatorType;

    /** 操作人员 */
    @Excel(name = "操作人员")
    @Schema(description = "操作人员", example = "admin")
    private String operName;

    /** 部门名称 */
    @Excel(name = "部门名称")
    @Schema(description = "部门名称", example = "研发部门")
    private String deptName;

    /** 请求url */
    @Excel(name = "请求地址")
    @Schema(description = "请求地址", example = "/system/user")
    private String operUrl;

    /** 操作地址 */
    @Excel(name = "操作地址")
    @Schema(description = "主机地址", example = "127.0.0.1")
    private String operIp;

    /** 操作地点 */
    @Excel(name = "操作地点")
    @Schema(description = "操作地点", example = "内网IP")
    private String operLocation;

    /** 请求参数 */
    @Excel(name = "请求参数")
    @Schema(description = "请求参数", example = "{\"userName\":\"howe\"}")
    private String operParam;

    /** 返回参数 */
    @Excel(name = "返回参数")
    @Schema(description = "返回参数", example = "{\"code\":200,\"msg\":\"操作成功\"}")
    private String jsonResult;

    /** 操作状态（0正常 1异常） */
    @Excel(name = "状态", readConverterExp = "0=正常,1=异常")
    @Schema(description = "操作状态（0正常 1异常）", example = "0")
    private Integer status;

    /** 错误消息 */
    @Excel(name = "错误消息")
    @Schema(description = "错误消息")
    private String errorMsg;

    /** 操作时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "操作时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "操作时间", example = "2026-07-31 10:00:00")
    private Date operTime;

    /** 消耗时间 */
    @Excel(name = "消耗时间", suffix = "毫秒")
    @Schema(description = "消耗时间（毫秒）", example = "35")
    private Long costTime;

    public Long getOperId()
    {
        return operId;
    }

    public void setOperId(Long operId)
    {
        this.operId = operId;
    }

    public String getTitle()
    {
        return title;
    }

    public void setTitle(String title)
    {
        this.title = title;
    }

    public Integer getBusinessType()
    {
        return businessType;
    }

    public void setBusinessType(Integer businessType)
    {
        this.businessType = businessType;
    }

    public Integer[] getBusinessTypes()
    {
        return businessTypes;
    }

    public void setBusinessTypes(Integer[] businessTypes)
    {
        this.businessTypes = businessTypes;
    }

    public String getMethod()
    {
        return method;
    }

    public void setMethod(String method)
    {
        this.method = method;
    }

    public String getRequestMethod()
    {
        return requestMethod;
    }

    public void setRequestMethod(String requestMethod)
    {
        this.requestMethod = requestMethod;
    }

    public Integer getOperatorType()
    {
        return operatorType;
    }

    public void setOperatorType(Integer operatorType)
    {
        this.operatorType = operatorType;
    }

    public String getOperName()
    {
        return operName;
    }

    public void setOperName(String operName)
    {
        this.operName = operName;
    }

    public String getDeptName()
    {
        return deptName;
    }

    public void setDeptName(String deptName)
    {
        this.deptName = deptName;
    }

    public String getOperUrl()
    {
        return operUrl;
    }

    public void setOperUrl(String operUrl)
    {
        this.operUrl = operUrl;
    }

    public String getOperIp()
    {
        return operIp;
    }

    public void setOperIp(String operIp)
    {
        this.operIp = operIp;
    }

    public String getOperLocation()
    {
        return operLocation;
    }

    public void setOperLocation(String operLocation)
    {
        this.operLocation = operLocation;
    }

    public String getOperParam()
    {
        return operParam;
    }

    public void setOperParam(String operParam)
    {
        this.operParam = operParam;
    }

    public String getJsonResult()
    {
        return jsonResult;
    }

    public void setJsonResult(String jsonResult)
    {
        this.jsonResult = jsonResult;
    }

    public Integer getStatus()
    {
        return status;
    }

    public void setStatus(Integer status)
    {
        this.status = status;
    }

    public String getErrorMsg()
    {
        return errorMsg;
    }

    public void setErrorMsg(String errorMsg)
    {
        this.errorMsg = errorMsg;
    }

    public Date getOperTime()
    {
        return operTime;
    }

    public void setOperTime(Date operTime)
    {
        this.operTime = operTime;
    }

    public Long getCostTime()
    {
        return costTime;
    }

    public void setCostTime(Long costTime)
    {
        this.costTime = costTime;
    }
}
