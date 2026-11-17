package com.howe.meal.controller;

import com.howe.common.core.controller.BaseController;
import com.howe.common.core.domain.AjaxResult;
import com.howe.meal.domain.vo.MealPrepPlan;
import com.howe.meal.service.MealPrepPlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 点餐-一键备菜 控制层
 *
 * <p>清单由下单后台线程生成，只存 Redis（TTL 10 天）；这里只提供厨师端读取与重新生成两个入口，
 * 权限沿用厨师工作台的 {@code meal:order:dispatch}，跨家庭读取被拒绝。</p>
 *
 * @author howe
 */
@Tag(name = "点餐-一键备菜", description = "读取或重新生成订单的 AI 备菜清单")
@RestController
@RequestMapping("/meal/prep-plan")
@RequiredArgsConstructor
public class MealPrepPlanController extends BaseController {

    private final MealPrepPlanService mealPrepPlanService;

    @Operation(summary = "读取备菜清单", description = "返回该订单的 AI 备菜清单与生成状态；未生成或已过期时 status=FAILED 并给出提示")
    @PreAuthorize("@ss.hasPermi('meal:order:dispatch')")
    @GetMapping("/{orderId}")
    public AjaxResult getPlan(@Parameter(description = "订单ID", required = true)
            @PathVariable("orderId") Long orderId) {
        return success(mealPrepPlanService.getPlan(orderId));
    }

    @Operation(summary = "重新生成备菜清单", description = "清单未生成、生成失败或已过期时重新提交后台生成任务；订单已结束则拒绝")
    @PreAuthorize("@ss.hasPermi('meal:order:dispatch')")
    @PostMapping("/{orderId}/regenerate")
    public AjaxResult regenerate(@Parameter(description = "订单ID", required = true)
            @PathVariable("orderId") Long orderId) {
        return success(mealPrepPlanService.regenerate(orderId));
    }
}
