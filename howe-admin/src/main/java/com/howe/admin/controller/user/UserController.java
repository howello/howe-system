package com.howe.admin.controller.user;

import com.github.pagehelper.PageInfo;
import com.howe.admin.service.auth.UserService;
import com.howe.common.dto.role.UserDTO;
import com.howe.common.utils.request.AjaxResult;
import com.howe.common.utils.token.UserInfoUtils;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import java.util.List;

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

    @GetMapping("/getUserList")
    @ApiOperation(value = "获取用户列表", httpMethod = "GET")
    public AjaxResult<List<UserDTO>> getUserList(UserDTO userDTO) {
        List<UserDTO> userList = userService.getUserList(userDTO);
        return AjaxResult.success(userList);
    }

    @GetMapping("/getUserPage")
    @ApiOperation(value = "分页获取用户列表", httpMethod = "GET")
    public AjaxResult<PageInfo<UserDTO>> getUserPage(UserDTO userDTO) {
        PageInfo<UserDTO> userPage = userService.getUserPage(userDTO);
        return AjaxResult.success(userPage);
    }

    @PutMapping("/changeUserStatus")
    @ApiOperation(value = "更改用户状态", httpMethod = "PUT")
    public AjaxResult<Boolean> changeUserStatus(String userId) {
        boolean b = userService.changeUserStatus(userId);
        return AjaxResult.success(b);
    }

    @GetMapping("/getUser")
    @ApiOperation(value = "获取用户", httpMethod = "GET")
    public AjaxResult<UserDTO> getUser(@Valid @NotBlank String userId) {
        UserDTO userDTO = userService.getById(userId);
        return AjaxResult.success(userDTO);
    }

    @PostMapping("/resetUserPwd")
    @ApiOperation(value = "重置密码", httpMethod = "POST")
    public AjaxResult<Boolean> resetUserPwd(@RequestBody UserDTO userDTO) {
        boolean b = userService.resetUserPwd(userDTO);
        return AjaxResult.success(b);
    }

    @PutMapping("/updateUser")
    @ApiOperation(value = "更新用户信息", httpMethod = "PUT")
    public AjaxResult<Boolean> updateUser(@RequestBody UserDTO userDTO) {
        Boolean b = userService.updateUser(userDTO);
        return AjaxResult.success(b);
    }

    @PutMapping("/addUser")
    @ApiOperation(value = "添加用户信息", httpMethod = "PUT")
    public AjaxResult<Boolean> addUser(@RequestBody UserDTO userDTO) {
        Boolean b = userService.addUser(userDTO);
        return AjaxResult.success(b);
    }

    @DeleteMapping("/delUser")
    @ApiOperation(value = "删除用户", httpMethod = "DELETE")
    public AjaxResult<Boolean> delUser(@RequestBody String[] userIds) {
        boolean b = userService.delUser(userIds);
        return AjaxResult.success(b);
    }
}
