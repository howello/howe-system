package com.howe.admin.controller.menu;

import com.howe.admin.service.menu.MenuService;
import com.howe.common.dto.menu.MenuDTO;
import com.howe.common.utils.request.AjaxResult;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

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
@RequiredArgsConstructor
public class MenuController {
    private final MenuService menuService;

    @PostMapping("/getMenuListWithPermit")
    @ApiOperation(value = "根据权限获取菜单列表", httpMethod = "post")
    public AjaxResult<List<MenuDTO>> getMenuListWithPermit() {
        List<MenuDTO> menuList = menuService.getMenuListWithPermission();
        return AjaxResult.success(menuList);
    }

    @GetMapping("/getMenuListByRoleId")
    @ApiOperation(value = "根据角色id获取菜单列表")
    public AjaxResult<List<MenuDTO>> getMenuListByRoleId(String roleId) {
        List<MenuDTO> menuList = menuService.getMenuListByRoleId(roleId);
        return AjaxResult.success(menuList);
    }

    @GetMapping("/getMenuList")
    @ApiOperation(value = "获取菜单列表", httpMethod = "get")
    public AjaxResult<List<MenuDTO>> getMenuList(MenuDTO menu) {
        List<MenuDTO> menuList = menuService.getMenuList(menu);
        return AjaxResult.success(menuList);
    }

    @GetMapping("/getMenu")
    @ApiOperation(value = "获取菜单", httpMethod = "get")
    public AjaxResult<MenuDTO> getMenu(@RequestParam("menuId") String menuId) {
        MenuDTO menuDTO = menuService.getById(menuId);
        return AjaxResult.success(menuDTO);
    }

    @PostMapping("/updateMenu")
    @ApiOperation(value = "更新菜单", httpMethod = "post")
    public AjaxResult<Boolean> updateMenu(@RequestBody MenuDTO menu) {
        boolean b = menuService.saveOrUpdate(menu);
        return AjaxResult.success(b);
    }

    @PostMapping("/saveMenu")
    @ApiOperation(value = "保存菜单", httpMethod = "post")
    public AjaxResult<Boolean> saveMenu(@RequestBody MenuDTO menu) {
        boolean save = menuService.save(menu);
        return AjaxResult.success(save);
    }

    @DeleteMapping("/delMenu")
    @ApiOperation(value = "删除菜单", httpMethod = "delete")
    public AjaxResult<Boolean> delMenu(String menuId) {
        boolean b = menuService.removeById(menuId);
        return AjaxResult.success(b);
    }
}
