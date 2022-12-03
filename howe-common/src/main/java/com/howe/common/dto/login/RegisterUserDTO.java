package com.howe.common.dto.login;

import lombok.Data;
import org.hibernate.validator.constraints.Length;

import javax.validation.constraints.NotBlank;

/**
 * <p>@Author lu
 * <p>@Date 2022/11/30 11:06 星期三
 * <p>@Version 1.0
 * <p>@Description 注册
 */
@Data
public class RegisterUserDTO {

    /**
     * 用户名
     */
    @NotBlank(message = "用户名不能为空")
    @Length(max = 20, min = 2, message = "用户名必须为2-20个字符之间")
    private String username;

    /**
     * 用户名
     */
    @Length(max = 20, min = 2, message = "昵称必须为2-20个字符之间")
    private String nickname;

    /**
     * 密码
     */
    @NotBlank(message = "密码不能为空")
    @Length(max = 25, min = 2, message = "密码必须在2-25个字符之间")
    private String password;

    /**
     * 验证码
     */
    private String code;

    /**
     * uuid
     */
    private String uuid = "";
}
