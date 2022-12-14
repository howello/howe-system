package com.howe.admin.controller.user;

import cn.hutool.core.util.ArrayUtil;
import com.github.pagehelper.PageInfo;
import com.howe.admin.service.auth.RoleService;
import com.howe.common.dto.role.RoleDTO;
import com.howe.common.enums.exception.AdminExceptionEnum;
import com.howe.common.exception.child.AdminException;
import com.howe.common.utils.request.AjaxResult;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.Arrays;
import java.util.List;

/**
 * <p>@Author lu
 * <p>@Date 2022/12/13 15:18 星期二
 * <p>@Version 1.0
 * <p>@Description 权限控制类
 */
@RequestMapping("/role")
@RestController
@Api(tags = "权限控制类")
@RequiredArgsConstructor
public class RoleController {
    private final RoleService roleService;

    @GetMapping("/getRoleList")
    @ApiOperation(value = "获取权限列表", httpMethod = "get")
    public AjaxResult<List<RoleDTO>> getRoleList(RoleDTO role) {
        List<RoleDTO> roleList = roleService.getRoleList(role);
        return AjaxResult.success(roleList);
    }

    @GetMapping("/getRolePage")
    @ApiOperation(value = "分页获取权限", httpMethod = "get")
    public AjaxResult<PageInfo<RoleDTO>> getRolePage(RoleDTO role) {
        PageInfo<RoleDTO> page = roleService.getRolePage(role);
        return AjaxResult.success(page);
    }

    @GetMapping("/getRole")
    @ApiOperation(value = "获取权限", httpMethod = "get")
    public AjaxResult<RoleDTO> getRole(String roleId) {
        RoleDTO role = roleService.getById(roleId);
        return AjaxResult.success(role);
    }

    @PostMapping("/changeRoleStatus")
    @ApiOperation(value = "改变角色状态", httpMethod = "post")
    public AjaxResult<Boolean> changeRoleStatus(@RequestBody RoleDTO roleDTO) {
        boolean b = roleService.updateById(roleDTO);
        return AjaxResult.success(b);
    }

    @PostMapping("/addRole")
    @ApiOperation(value = "添加一个角色", httpMethod = "post")
    public AjaxResult<Boolean> addRole(@RequestBody RoleDTO role) {
        boolean save = roleService.save(role);
        return AjaxResult.success(save);
    }

    @PutMapping("/updateRole")
    @ApiOperation(value = "更新角色信息", httpMethod = "put")
    public AjaxResult<Boolean> updateRole(@RequestBody RoleDTO role) {
        boolean b = roleService.updateById(role);
        return AjaxResult.success(b);
    }

    @SneakyThrows
    @DeleteMapping("/delRole")
    @ApiOperation(value = "删除角色信息", httpMethod = "delete")
    public AjaxResult<Boolean> delRole(@RequestBody @Valid @NotNull String[] roleId) {
        if (ArrayUtil.isEmpty(roleId)) {
            throw new AdminException(AdminExceptionEnum.PARAMETER_INVALID);
        }
        boolean b = roleService.removeByIds(Arrays.asList(roleId));
        return AjaxResult.success(b);
    }
}
