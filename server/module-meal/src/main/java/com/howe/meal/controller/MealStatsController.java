package com.howe.meal.controller;

import com.howe.common.core.controller.BaseController;
import com.howe.common.core.domain.AjaxResult;
import com.howe.meal.service.IMealStatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 点餐-首页统计 控制层
 *
 * <p>供管理端首页卡片取数。每个接口独立权限校验，权限点沿用既有权限，
 * 不新增权限点；数据范围由服务层按与列表接口相同的本部门规则隔离。</p>
 *
 * @author howe
 */
@Tag(name = "点餐-首页统计", description = "管理端首页点餐相关卡片数据")
@RestController
@RequestMapping("/meal/stats")
@RequiredArgsConstructor
public class MealStatsController extends BaseController {

    private final IMealStatsService mealStatsService;

    @Operation(summary = "点餐统计", description = "今日订单数、状态分布、近 7 天趋势与评价均分")
    @PreAuthorize("@ss.hasAnyPermi('meal:order:list,meal:review:list')")
    @GetMapping("/summary")
    public AjaxResult summary() {
        return success(mealStatsService.getHomeStats());
    }

    @Operation(summary = "菜品概况", description = "上架/下架菜品数、分类数、公共与家庭菜谱数")
    @PreAuthorize("@ss.hasPermi('meal:dish:list')")
    @GetMapping("/dish")
    public AjaxResult dish() {
        return success(mealStatsService.getDishOverview());
    }

    @Operation(summary = "App 版本概况", description = "最新已发布版本、发布时间、是否强制更新与记录总数")
    @PreAuthorize("@ss.hasPermi('meal:appVersion:list')")
    @GetMapping("/appVersion")
    public AjaxResult appVersion() {
        return success(mealStatsService.getAppVersionOverview());
    }

    @Operation(summary = "热门菜品", description = "近 30 天已完成订单中被点份数 Top 5")
    @PreAuthorize("@ss.hasPermi('meal:order:list')")
    @GetMapping("/hotDishes")
    public AjaxResult hotDishes() {
        return success(mealStatsService.getHotDishes());
    }

    @Operation(summary = "待办：待审提案数", description = "供首页待办聚合卡使用")
    @PreAuthorize("@ss.hasPermi('meal:proposal:list')")
    @GetMapping("/pendingProposals")
    public AjaxResult pendingProposals() {
        return success(mealStatsService.countPendingProposals());
    }

    @Operation(summary = "待办：待接订单数", description = "供首页待办聚合卡使用")
    @PreAuthorize("@ss.hasAnyPermi('meal:order:list,meal:order:dispatch')")
    @GetMapping("/pendingOrders")
    public AjaxResult pendingOrders() {
        return success(mealStatsService.countPendingOrders());
    }

    @Operation(summary = "个人足迹：我的订单数", description = "供首页个人足迹卡使用")
    @GetMapping("/myOrders")
    public AjaxResult myOrders() {
        return success(mealStatsService.countMyOrders());
    }

    @Operation(summary = "个人足迹：我提交的提案数", description = "供首页个人足迹卡使用")
    @GetMapping("/myProposals")
    public AjaxResult myProposals() {
        return success(mealStatsService.countMyProposals());
    }
}
