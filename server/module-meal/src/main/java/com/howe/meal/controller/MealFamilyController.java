package com.howe.meal.controller;

import com.howe.common.core.controller.BaseController;
import com.howe.common.core.domain.AjaxResult;
import com.howe.common.exception.ServiceException;
import com.howe.common.utils.SecurityUtils;
import com.howe.system.service.IFamilyCodeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 点餐-家庭邀请码 控制层
 *
 * <p>家庭管理员在点餐端「家庭与成员」页查看或重新生成邀请码，发给要加入的家人。
 * 家庭归属一律由服务端按登录用户确定，不接受前端传入的 {@code deptId}，
 * 避免越权给别人的家庭生成码。</p>
 *
 * @author howe
 */
@Tag(name = "点餐-家庭邀请码", description = "家庭管理员在手机端查看/生成加入本家庭的邀请码")
@RestController
@RequestMapping("/meal/family")
@RequiredArgsConstructor
public class MealFamilyController extends BaseController {

    private final IFamilyCodeService familyCodeService;

    @PreAuthorize("@ss.hasRole('meal_manager')")
    @Operation(summary = "查询本家庭邀请码", description = "返回当前邀请码与剩余有效期；没有或已过期时 code 为空")
    @GetMapping("/code")
    public AjaxResult getCode() {
        return success(familyCodeService.describe(currentDeptId()));
    }

    @PreAuthorize("@ss.hasRole('meal_manager')")
    @Operation(summary = "生成本家庭邀请码", description = "有效期 1 天；重新生成会让旧码立即失效")
    @PostMapping("/code")
    public AjaxResult generateCode() {
        Long deptId = currentDeptId();
        return success(familyCodeService.describe(deptId, familyCodeService.generateCode(deptId)));
    }

    /**
     * 取当前登录用户的家庭
     *
     * @return 家庭部门ID
     * @throws ServiceException 未归属家庭时抛出
     */
    private Long currentDeptId() {
        Long deptId = SecurityUtils.getDeptId();
        if (deptId == null) {
            throw new ServiceException("当前账号未归属任何家庭");
        }
        return deptId;
    }
}
