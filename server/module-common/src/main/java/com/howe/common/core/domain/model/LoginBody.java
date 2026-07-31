package com.howe.common.core.domain.model;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 用户登录对象
 *
 * @author howe
 */
@Schema(description = "用户登录对象")
public class LoginBody
{
    /**
     * 用户名
     */
    @Schema(description = "用户名", example = "admin")
    private String username;

    /**
     * 用户密码
     */
    @Schema(description = "用户密码", example = "admin123")
    private String password;

    /**
     * 验证码
     */
    @Schema(description = "验证码", example = "1234")
    private String code;

    /**
     * 唯一标识
     */
    @Schema(description = "验证码唯一标识（uuid）")
    private String uuid;

    /**
     * Cloudflare Turnstile 令牌
     *
     * <p>
     * 只有在参数配置里开了 {@code sys.turnstile.enabled} 时才会被校验，
     * 未开启时前端不传也没关系。
     * </p>
     */
    @Schema(description = "Cloudflare Turnstile 人机校验令牌，仅在参数配置 sys.turnstile.enabled 开启时才校验，未开启时可不传")
    private String turnstileToken;

    public String getUsername()
    {
        return username;
    }

    public void setUsername(String username)
    {
        this.username = username;
    }

    public String getPassword()
    {
        return password;
    }

    public void setPassword(String password)
    {
        this.password = password;
    }

    public String getCode()
    {
        return code;
    }

    public void setCode(String code)
    {
        this.code = code;
    }

    public String getUuid()
    {
        return uuid;
    }

    public void setUuid(String uuid)
    {
        this.uuid = uuid;
    }

    public String getTurnstileToken()
    {
        return turnstileToken;
    }

    public void setTurnstileToken(String turnstileToken)
    {
        this.turnstileToken = turnstileToken;
    }
}
