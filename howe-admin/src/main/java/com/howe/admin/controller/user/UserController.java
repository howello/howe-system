package com.howe.admin.controller.user;

import com.howe.admin.service.auth.UserService;
import com.howe.common.dto.role.UserDTO;
import com.howe.common.utils.request.AjaxResult;
import com.howe.common.utils.token.UserInfoUtils;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * <p>@Author lu
 * <p>@Date 2022/12/1 17:25 星期四
 * <p>@Version 1.0
 * <p>@Description 用户相关
 */
@RequestMapping("/user")
@RestController
@Api(tags = "用户相关")
@RequiredArgsConstructor
public class UserController {
    private final UserInfoUtils userInfoUtils;

    private final UserService userService;

    @GetMapping("/info")
    @ApiOperation(value = "查询用户信息", httpMethod = "GET")
    public AjaxResult<UserDTO> getUserInfo() {
        return AjaxResult.success(userInfoUtils.getUserInfo());
    }
}
