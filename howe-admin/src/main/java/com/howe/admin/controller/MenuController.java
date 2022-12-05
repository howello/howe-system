package com.howe.admin.controller;

import com.howe.admin.service.menu.MenuService;
import com.howe.common.dto.menu.MenuDTO;
import com.howe.common.utils.request.AjaxResult;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * <p>@Author lu
 * <p>@Date 2022/11/4 10:52 星期五
 * <p>@Version 1.0
 * <p>@Description
 */
@RestController
@RequestMapping("/menu")
@Validated
@Api(tags = "菜单按钮")
public class MenuController {
    @Autowired
    private MenuService menuService;

    @PostMapping("/getMenuList")
    @ApiOperation(value = "获取菜单列表", httpMethod = "POST")
    public AjaxResult<List<MenuDTO>> getMenuList() {
        List<MenuDTO> menuList = menuService.getMenuListWithPermission();
        return AjaxResult.success(menuList);
    }
}
