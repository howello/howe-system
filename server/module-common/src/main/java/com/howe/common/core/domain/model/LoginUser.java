package com.howe.common.core.domain.model;

import com.alibaba.fastjson2.annotation.JSONField;
import com.howe.common.core.domain.entity.SysUser;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import java.util.Collection;
import java.util.Set;

/**
 * 登录用户身份权限
 *
 * @author howe
 */
@Schema(description = "登录用户身份权限信息")
public class LoginUser implements UserDetails
{
    private static final long serialVersionUID = 1L;

    /**
     * 用户ID
     */
    @Schema(description = "用户ID", example = "1")
    private Long userId;

    /**
     * 部门ID
     */
    @Schema(description = "部门ID", example = "103")
    private Long deptId;

    /**
     * 用户唯一标识
     */
    @Schema(description = "用户唯一标识（缓存 token 的 key），内部使用", accessMode = Schema.AccessMode.READ_ONLY)
    private String token;

    /**
     * 登录时间
     */
    @Schema(description = "登录时间戳（毫秒）", accessMode = Schema.AccessMode.READ_ONLY, example = "1767225600000")
    private Long loginTime;

    /**
     * 过期时间
     */
    @Schema(description = "过期时间戳（毫秒）", accessMode = Schema.AccessMode.READ_ONLY, example = "1767227400000")
    private Long expireTime;

    /**
     * 登录IP地址
     */
    @Schema(description = "登录IP地址", accessMode = Schema.AccessMode.READ_ONLY, example = "127.0.0.1")
    private String ipaddr;

    /**
     * 登录地点
     */
    @Schema(description = "登录地点", accessMode = Schema.AccessMode.READ_ONLY, example = "内网IP")
    private String loginLocation;

    /**
     * 浏览器类型
     */
    @Schema(description = "浏览器类型", accessMode = Schema.AccessMode.READ_ONLY, example = "Chrome")
    private String browser;

    /**
     * 操作系统
     */
    @Schema(description = "操作系统", accessMode = Schema.AccessMode.READ_ONLY, example = "Windows 11")
    private String os;

    /**
     * 权限列表
     */
    @Schema(description = "权限标识列表", accessMode = Schema.AccessMode.READ_ONLY)
    private Set<String> permissions;

    /**
     * 用户信息
     */
    @Schema(description = "用户信息", accessMode = Schema.AccessMode.READ_ONLY)
    private SysUser user;

    public LoginUser()
    {
    }

    public LoginUser(SysUser user, Set<String> permissions)
    {
        this.user = user;
        this.permissions = permissions;
    }

    public LoginUser(Long userId, Long deptId, SysUser user, Set<String> permissions)
    {
        this.userId = userId;
        this.deptId = deptId;
        this.user = user;
        this.permissions = permissions;
    }

    public Long getUserId()
    {
        return userId;
    }

    public void setUserId(Long userId)
    {
        this.userId = userId;
    }

    public Long getDeptId()
    {
        return deptId;
    }

    public void setDeptId(Long deptId)
    {
        this.deptId = deptId;
    }

    public String getToken()
    {
        return token;
    }

    public void setToken(String token)
    {
        this.token = token;
    }

    @JSONField(serialize = false)
    @Override
    public String getPassword()
    {
        return user.getPassword();
    }

    @Override
    public String getUsername()
    {
        return user.getUserName();
    }

    /**
     * 账户是否未过期,过期无法验证
     */
    @JSONField(serialize = false)
    @Override
    public boolean isAccountNonExpired()
    {
        return true;
    }

    /**
     * 指定用户是否解锁,锁定的用户无法进行身份验证
     *
     * @return
     */
    @JSONField(serialize = false)
    @Override
    public boolean isAccountNonLocked()
    {
        return true;
    }

    /**
     * 指示是否已过期的用户的凭据(密码),过期的凭据防止认证
     *
     * @return
     */
    @JSONField(serialize = false)
    @Override
    public boolean isCredentialsNonExpired()
    {
        return true;
    }

    /**
     * 是否可用 ,禁用的用户不能身份验证
     *
     * @return
     */
    @JSONField(serialize = false)
    @Override
    public boolean isEnabled()
    {
        return true;
    }

    public Long getLoginTime()
    {
        return loginTime;
    }

    public void setLoginTime(Long loginTime)
    {
        this.loginTime = loginTime;
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

    public Long getExpireTime()
    {
        return expireTime;
    }

    public void setExpireTime(Long expireTime)
    {
        this.expireTime = expireTime;
    }

    public Set<String> getPermissions()
    {
        return permissions;
    }

    public void setPermissions(Set<String> permissions)
    {
        this.permissions = permissions;
    }

    public SysUser getUser()
    {
        return user;
    }

    public void setUser(SysUser user)
    {
        this.user = user;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities()
    {
        return null;
    }
}
