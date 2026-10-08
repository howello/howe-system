package com.howe.meal.controller;

import com.howe.common.annotation.Log;
import com.howe.common.core.controller.BaseController;
import com.howe.common.core.domain.AjaxResult;
import com.howe.common.core.page.TableDataInfo;
import com.howe.common.enums.BusinessType;
import com.howe.meal.domain.MealOrder;
import com.howe.meal.domain.dto.MealOrderSubmitBody;
import com.howe.meal.service.IMealOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 点餐-订单 控制层
 *
 * <p>点餐端的下单、我的订单、详情、取消都只要求登录；厨师端的接单与完成
 * 分别由 {@code meal:order:dispatch}、{@code meal:order:finish} 控制；
 * 管理端的列表与删除由 {@code meal:order:list}、{@code meal:order:remove} 控制。</p>
 *
 * @author howe
 */
@Tag(name = "点餐-订单", description = "下单、我的订单、厨师接单、管理端订单查询")
@RestController
@RequestMapping("/meal/order")
@RequiredArgsConstructor
public class MealOrderController extends BaseController {

    private final IMealOrderService mealOrderService;

    @Operation(summary = "下单", description = "购物车在前端本地，下单时一次性提交；菜名与封面由后端落成快照")
    @Log(title = "点餐订单", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Valid @RequestBody MealOrderSubmitBody body) {
        return success(mealOrderService.submitOrder(body));
    }

    @Operation(summary = "我的订单", description = "只返回当前登录用户自己的订单")
    @GetMapping("/my")
    public TableDataInfo my(MealOrder mealOrder) {
        startPage();
        return getDataTable(mealOrderService.selectMyOrderList(mealOrder));
    }

    @Operation(summary = "订单详情", description = "含状态时间轴所需的接单/完成时间与点餐明细；跨家庭读取被拒绝")
    @GetMapping(value = "/{orderId}")
    public AjaxResult getInfo(@Parameter(description = "订单ID", required = true)
            @PathVariable("orderId") Long orderId) {
        return success(mealOrderService.selectMealOrderDetail(orderId));
    }

    @Operation(summary = "取消订单", description = "只能取消本人尚未被接单的订单")
    @Log(title = "点餐订单", businessType = BusinessType.UPDATE)
    @PutMapping("/cancel/{orderId}")
    public AjaxResult cancel(@Parameter(description = "订单ID", required = true)
            @PathVariable("orderId") Long orderId) {
        return toAjax(mealOrderService.cancelOrder(orderId));
    }

    @Operation(summary = "厨师工作台订单列表", description = "按状态查本家庭订单，附带点餐明细与等待/制作时长")
    @PreAuthorize("@ss.hasPermi('meal:order:dispatch')")
    @GetMapping("/kitchen/list")
    public TableDataInfo kitchenList(MealOrder mealOrder) {
        startPage();
        return getDataTable(mealOrderService.selectKitchenOrderList(mealOrder));
    }

    @Operation(summary = "接单", description = "待接单 → 制作中")
    @PreAuthorize("@ss.hasPermi('meal:order:dispatch')")
    @Log(title = "点餐订单", businessType = BusinessType.UPDATE)
    @PutMapping("/accept/{orderId}")
    public AjaxResult accept(@Parameter(description = "订单ID", required = true)
            @PathVariable("orderId") Long orderId) {
        return toAjax(mealOrderService.acceptOrder(orderId));
    }

    @Operation(summary = "标记完成", description = "制作中 → 已完成")
    @PreAuthorize("@ss.hasPermi('meal:order:finish')")
    @Log(title = "点餐订单", businessType = BusinessType.UPDATE)
    @PutMapping("/finish/{orderId}")
    public AjaxResult finish(@Parameter(description = "订单ID", required = true)
            @PathVariable("orderId") Long orderId) {
        return toAjax(mealOrderService.finishOrder(orderId));
    }

    @Operation(summary = "查询订单列表（管理端）", description = "受数据权限约束，可跨状态与时间筛选本家庭订单")
    @PreAuthorize("@ss.hasPermi('meal:order:list')")
    @GetMapping("/list")
    public TableDataInfo list(MealOrder mealOrder) {
        startPage();
        return getDataTable(mealOrderService.selectMealOrderList(mealOrder));
    }

    @Operation(summary = "删除订单")
    @PreAuthorize("@ss.hasPermi('meal:order:remove')")
    @Log(title = "点餐订单", businessType = BusinessType.DELETE)
    @DeleteMapping("/{orderIds}")
    public AjaxResult remove(@Parameter(description = "订单ID数组，多个用逗号分隔", required = true)
            @PathVariable Long[] orderIds) {
        return toAjax(mealOrderService.deleteMealOrderByIds(orderIds));
    }
}
