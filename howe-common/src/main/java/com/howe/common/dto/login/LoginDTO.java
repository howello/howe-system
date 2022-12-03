package com.howe.common.dto.login;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * <p>@Author lu
 * <p>@Date 2022/11/8 8:48 星期二
 * <p>@Version 1.0
 * <p>@Description
 */
@Data
@ApiModel("用户登录")
public class LoginDTO {
    /**
     * 用户名
     */
    @NotBlank(message = "用户名不能为空")
    @ApiModelProperty("用户名")
    private String username;

    /**
     * 密码
     */
    @NotBlank(message = "密码不能为空")
    @ApiModelProperty("密码")
    private String password;

    /**
     * 验证码
     */
    @NotBlank(message = "验证码不能为空")
    @ApiModelProperty("验证码")
    private String code;

    /**
     * uuid 唯一标识
     */
    @ApiModelProperty("uuid")
    private String uuid = "";

}
