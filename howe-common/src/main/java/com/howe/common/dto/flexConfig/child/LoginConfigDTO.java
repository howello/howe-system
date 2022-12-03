package com.howe.common.dto.flexConfig.child;

import com.howe.common.dto.flexConfig.BaseConfigItemDTO;
import lombok.Data;

import java.util.List;

/**
 * <p>@Author lu
 * <p>@Date 2022/11/28 16:18 星期一
 * <p>@Version 1.0
 * <p>@Description TODO
 */
@Data
public class LoginConfigDTO implements BaseConfigItemDTO {

    /**
     * 验证码开关
     * true -- 有验证码
     * false -- 无验证码
     */
    private Boolean captchaSwitch = true;

    /**
     * 是否启用用户注册
     * true -- 启用
     * false -- 不启用
     */
    private Boolean registerUser = true;

    /**
     * 内置用户，这些用户名不能注册
     */
    private List<String> builtInUser;
}
