package com.howe.common.dto.login;

import io.swagger.annotations.ApiModelProperty;
import io.swagger.annotations.ApiOperation;
import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

/**
 * <p>@Author lu
 * <p>@Date 2022/12/1 9:05 星期四
 * <p>@Version 1.0
 * <p>@Description 验证码出参
 */
@Data
@ApiOperation("验证码出参")
public class ValidCodeDTO {
    /**
     * 验证码高度
     */
    @ApiModelProperty("验证码高度")
    @NotNull(message = "验证码高度不能为空")
    @Min(value = 20, message = "验证码高度不能小于20")
    private Integer height;

    /**
     * 验证码宽度
     */
    @ApiModelProperty("验证码宽度")
    @NotNull(message = "验证码宽度不能为空")
    @Min(value = 20, message = "验证码宽度不能小于20")
    private Integer width;

    /**
     * 验证码开关
     */
    @ApiModelProperty("验证码开关")
    private Boolean captchaOnOff;

    @ApiModelProperty("验证码图片Base64")
    private String codeImg;

    @ApiModelProperty("验证码uuid")
    private String uuid;
}
