package com.howe.admin.service.login;

import com.howe.common.dto.login.LoginDTO;
import com.howe.common.dto.login.RegisterUserDTO;
import com.howe.common.dto.login.ValidCodeDTO;

/**
 * <p>@Author lu
 * <p>@Date 2022/11/7 17:07 星期一
 * <p>@Version 1.0
 * <p>@Description
 */
public interface LoginService {
    /**
     * 登录
     *
     * @param loginDTO
     * @return
     */
    String login(LoginDTO loginDTO);

    /**
     * 注册
     *
     * @param registerUserDTO
     * @return
     */
    Boolean register(RegisterUserDTO registerUserDTO);

    /**
     * 获取验证码
     *
     * @return
     */
    ValidCodeDTO getValidCode(ValidCodeDTO validCodeDTO);
}
