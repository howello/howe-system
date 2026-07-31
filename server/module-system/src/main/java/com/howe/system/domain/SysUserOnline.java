package com.howe.system.domain;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 当前在线会话
 *
 * @author howe
 */
@Schema(description = "当前在线会话")
public class SysUserOnline
{
    /** 会话编号 */
    @Schema(description = "会话编号", example = "e2b7e0f1-3c1a-4a9e-8f0c-2b1d4e5f6a7b")
    private String tokenId;

    /** 部门名称 */
    @Schema(description = "部门名称", example = "研发部门")
    private String deptName;

    /** 用户名称 */
    @Schema(description = "用户名称", example = "admin")
    private String userName;

    /** 登录IP地址 */
    @Schema(description = "登录IP地址", example = "127.0.0.1")
    private String ipaddr;

    /** 登录地址 */
    @Schema(description = "登录地点", example = "内网IP")
    private String loginLocation;

    /** 浏览器类型 */
    @Schema(description = "浏览器类型", example = "Chrome 12")
    private String browser;

    /** 操作系统 */
    @Schema(description = "操作系统", example = "Windows 11")
    private String os;

    /** 登录时间 */
    @Schema(description = "登录时间戳（毫秒）", example = "1753929600000")
    private Long loginTime;

    public String getTokenId()
    {
        return tokenId;
    }

    public void setTokenId(String tokenId)
    {
        this.tokenId = tokenId;
    }

    public String getDeptName()
    {
        return deptName;
    }

    public void setDeptName(String deptName)
    {
        this.deptName = deptName;
    }

    public String getUserName()
    {
        return userName;
    }

    public void setUserName(String userName)
    {
        this.userName = userName;
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

    public Long getLoginTime()
    {
        return loginTime;
    }

    public void setLoginTime(Long loginTime)
    {
        this.loginTime = loginTime;
    }
}
