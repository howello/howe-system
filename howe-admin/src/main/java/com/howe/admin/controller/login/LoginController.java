package com.howe.admin.controller.login;

import com.howe.admin.service.login.LoginService;
import com.howe.common.dto.flexConfig.child.LoginConfigDTO;
import com.howe.common.dto.login.LoginDTO;
import com.howe.common.dto.login.RegisterUserDTO;
import com.howe.common.dto.login.ValidCodeDTO;
import com.howe.common.enums.exception.AdminExceptionEnum;
import com.howe.common.enums.flexConfig.FlexConfigBizTypeEnum;
import com.howe.common.utils.config.FlexConfigUtils;
import com.howe.common.utils.request.AjaxResult;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

/**
 * <p>@Author lu
 * <p>@Date 2022/11/7 16:37 星期一
 * <p>@Version 1.0
 * <p>@Description
 */
@RestController
@RequestMapping("/user")
@Api(tags = "登录相关")
@RequiredArgsConstructor
public class LoginController {

    private final LoginService loginService;
    private final FlexConfigUtils flexConfigUtils;

    /**
     * 登录
     *
     * @param loginDTO
     * @return
     */
    @PostMapping("/login")
    @ApiOperation(value = "登录接口", httpMethod = "POST")
    public AjaxResult<String> login(@RequestBody @Valid LoginDTO loginDTO) {
        String token = loginService.login(loginDTO);
        return AjaxResult.success(AjaxResult.MSG_SUCCESS, token);
    }

    /**
     * 注册
     *
     * @param registerUserDTO
     * @return
     */
    @PostMapping("/register")
    @ApiOperation(value = "注册", httpMethod = "POST")
    public AjaxResult<Boolean> register(@RequestBody @Valid RegisterUserDTO registerUserDTO) {
        LoginConfigDTO config = flexConfigUtils.getConfig(FlexConfigBizTypeEnum.LOGIN_CONFIG.getCode(), LoginConfigDTO.class);
        if (config.getRegisterUser()) {
            return AjaxResult.error(AdminExceptionEnum.DISABLE_REGISTER);
        }
        Boolean register = loginService.register(registerUserDTO);
        return AjaxResult.success(register);
    }

    @PostMapping("/getValidCode")
    @ApiOperation(value = "获取验证码接口", httpMethod = "POST")
    public AjaxResult<ValidCodeDTO> getValidCode(@RequestBody @Valid ValidCodeDTO validCodeDTO) {
        return AjaxResult.success(loginService.getValidCode(validCodeDTO));
    }
}
