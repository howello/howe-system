package com.howe.system.domain;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.howe.common.annotation.Excel;
import com.howe.common.annotation.Excel.ColumnType;
import com.howe.common.core.domain.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 系统访问记录表 sys_logininfor
 *
 * @author howe
 */
@Schema(description = "系统访问记录")
public class SysLogininfor extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** ID */
    @Excel(name = "序号", cellType = ColumnType.NUMERIC)
    @Schema(description = "访问ID", example = "1")
    private Long infoId;

    /** 用户账号 */
    @Excel(name = "用户账号")
    @Schema(description = "用户账号", example = "admin")
    private String userName;

    /** 登录状态 0成功 1失败 */
    @Excel(name = "登录状态", readConverterExp = "0=成功,1=失败")
    @Schema(description = "登录状态（0成功 1失败）", example = "0")
    private String status;

    /** 登录IP地址 */
    @Excel(name = "登录地址")
    @Schema(description = "登录IP地址", example = "127.0.0.1")
    private String ipaddr;

    /** 登录地点 */
    @Excel(name = "登录地点")
    @Schema(description = "登录地点", example = "内网IP")
    private String loginLocation;

    /** 浏览器类型 */
    @Excel(name = "浏览器")
    @Schema(description = "浏览器类型", example = "Chrome 12")
    private String browser;

    /** 操作系统 */
    @Excel(name = "操作系统")
    @Schema(description = "操作系统", example = "Windows 11")
    private String os;

    /** 提示消息 */
    @Excel(name = "提示消息")
    @Schema(description = "提示消息", example = "登录成功")
    private String msg;

    /** 访问时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "访问时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "访问时间", example = "2026-07-31 10:00:00")
    private Date loginTime;

    public Long getInfoId()
    {
        return infoId;
    }

    public void setInfoId(Long infoId)
    {
        this.infoId = infoId;
    }

    public String getUserName()
    {
        return userName;
    }

    public void setUserName(String userName)
    {
        this.userName = userName;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getIpaddr()
    {
        return ipaddr;
    }

    public void setIpaddr(String ipaddr)
    {
        this.ipaddr = ipaddr;
    }

    public String getLoginLocation()
    {
        return loginLocation;
    }

    public void setLoginLocation(String loginLocation)
    {
        this.loginLocation = loginLocation;
    }

    public String getBrowser()
    {
        return browser;
    }

    public void setBrowser(String browser)
    {
        this.browser = browser;
    }

    public String getOs()
    {
        return os;
    }

    public void setOs(String os)
    {
        this.os = os;
    }

    public String getMsg()
    {
        return msg;
    }

    public void setMsg(String msg)
    {
        this.msg = msg;
    }

    public Date getLoginTime()
    {
        return loginTime;
    }

    public void setLoginTime(Date loginTime)
    {
        this.loginTime = loginTime;
    }
}
