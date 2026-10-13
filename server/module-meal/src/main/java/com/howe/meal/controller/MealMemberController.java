package com.howe.meal.controller;

import com.howe.common.annotation.Log;
import com.howe.common.core.controller.BaseController;
import com.howe.common.core.domain.AjaxResult;
import com.howe.common.enums.BusinessType;
import com.howe.meal.domain.dto.MealMemberBody;
import com.howe.meal.domain.dto.MealMemberStatusBody;
import com.howe.meal.service.IMealMemberService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 点餐-家庭成员 控制层
 *
 * <p>家庭管理员在点餐端「家庭与成员」页维护本家庭成员。归属家庭一律由服务端按
 * 登录用户确定，不接受前端传入的 {@code deptId}；跨家庭、操作平台超管、
 * 停用或移除自己、移除最后一名家庭管理员都会被拒绝。</p>
 *
 * @author howe
 */
@Tag(name = "点餐-家庭成员", description = "家庭管理员在手机端管理本家庭成员与家庭角色")
@RestController
@RequestMapping("/meal/member")
@RequiredArgsConstructor
public class MealMemberController extends BaseController {

    private final IMealMemberService mealMemberService;

    @PreAuthorize("@ss.hasRole('meal_manager')")
    @Operation(summary = "查询本家庭成员列表", description = "含成员已分配的家庭角色；家庭管理员只能看到本家庭成员")
    @GetMapping("/list")
    public AjaxResult list(@Parameter(description = "账号或昵称关键字")
                           @RequestParam(value = "keyword", required = false) String keyword) {
        return success(mealMemberService.selectFamilyMemberList(keyword));
    }

    @PreAuthorize("@ss.hasRole('meal_manager')")
    @Operation(summary = "查询可分配的家庭角色", description = "返回点餐员 / 厨师 / 家庭管理员，供成员表单选择")
    @GetMapping("/roles")
    public AjaxResult roles() {
        return success(mealMemberService.selectAssignableRoles());
    }

    @PreAuthorize("@ss.hasRole('meal_manager')")
    @Log(title = "家庭成员", businessType = BusinessType.INSERT)
    @Operation(summary = "新增本家庭成员", description = "家庭管理员直接创建成员账号并设置初始密码与家庭角色")
    @PostMapping
    public AjaxResult add(@RequestBody MealMemberBody body) {
        mealMemberService.addMember(body);
        return success();
    }

    @PreAuthorize("@ss.hasRole('meal_manager')")
    @Log(title = "家庭成员", businessType = BusinessType.UPDATE)
    @Operation(summary = "修改本家庭成员", description = "修改昵称、手机号与家庭角色")
    @PutMapping
    public AjaxResult edit(@RequestBody MealMemberBody body) {
        mealMemberService.updateMember(body);
        return success();
    }

    @PreAuthorize("@ss.hasRole('meal_manager')")
    @Log(title = "家庭成员", businessType = BusinessType.UPDATE)
    @Operation(summary = "停用/启用本家庭成员", description = "停用后该成员不能登录")
    @PutMapping("/status")
    public AjaxResult changeStatus(@RequestBody MealMemberStatusBody body) {
        mealMemberService.updateMemberStatus(body.getUserId(), body.getStatus());
        return success();
    }

    @PreAuthorize("@ss.hasRole('meal_manager')")
    @Log(title = "家庭成员", businessType = BusinessType.DELETE)
    @Operation(summary = "移除本家庭成员", description = "逻辑删除，保留历史数据关联")
    @DeleteMapping("/{userId}")
    public AjaxResult remove(@Parameter(description = "成员ID", required = true)
                             @PathVariable("userId") Long userId) {
        mealMemberService.removeMember(userId);
        return success();
    }
}
