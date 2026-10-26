package com.howe.meal.controller;

import com.howe.common.core.controller.BaseController;
import com.howe.common.core.domain.AjaxResult;
import com.howe.common.constant.HttpStatus;
import com.howe.common.exception.ServiceException;
import com.howe.common.utils.SecurityUtils;
import com.howe.meal.domain.MealOrder;
import com.howe.meal.domain.MealNotification;
import com.howe.meal.domain.vo.MealKitchenOrderSummary;
import com.howe.meal.domain.vo.MealNotifyOverview;
import com.howe.meal.service.IMealOrderService;
import com.howe.meal.service.IMealNotifyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * 点餐订单通知控制层。
 */
@Tag(name = "点餐-通知", description = "轮询当前登录用户的订单通知")
@RestController
@RequestMapping("/meal/notify")
@RequiredArgsConstructor
@Slf4j
public class MealNotifyController extends BaseController {

    private static final String ROLE_USER = "user";
    private static final String ROLE_CHEF = "chef";
    private static final String ROLE_MEAL_EATER = "meal_eater";
    private static final String ROLE_MEAL_CHEF = "meal_chef";
    private static final String ROLE_MEAL_MANAGER = "meal_manager";
    private final IMealNotifyService mealNotifyService;
    private final IMealOrderService mealOrderService;

    /**
     * 聚合轮询当前用户的通知与厨师待办数量。
     *
     * <p>厨师一次读取点餐人与厨房两个队列，并顺带返回角标统计；其他角色只读取点餐人队列。
     * 统计失败不影响已读取的通知返回，避免通知因附加统计异常而丢失。</p>
     *
     * @return 本轮通知与可选厨师待办数量
     */
    @Operation(summary = "聚合轮询通知与厨师待办", description = "一次请求返回通知；厨师同时返回待接单与制作中数量")
    @GetMapping("/overview")
    public AjaxResult overview() {
        Long userId = SecurityUtils.getUserId();
        boolean chef = SecurityUtils.hasRole(ROLE_MEAL_CHEF);
        boolean canReadUserQueue = chef || SecurityUtils.hasRole(ROLE_MEAL_EATER)
                || SecurityUtils.hasRole(ROLE_MEAL_MANAGER);
        if (!canReadUserQueue) {
            throw new ServiceException("没有权限读取点餐通知", HttpStatus.FORBIDDEN);
        }

        List<MealNotification> notifications = new ArrayList<>();
        notifications.addAll(mealNotifyService.poll("notify:user:" + userId));
        Long pendingCount = null;
        Long cookingCount = null;
        if (chef) {
            notifications.addAll(mealNotifyService.poll("notify:chef:" + userId));
            try {
                MealKitchenOrderSummary summary = mealOrderService.countKitchenOrderSummary(new MealOrder());
                pendingCount = summary.pendingCount();
                cookingCount = summary.cookingCount();
            } catch (Exception e) {
                // 队列已读取并清空，统计失败也必须正常返回通知，避免这一轮通知丢失。
                log.warn("聚合轮询厨师待办统计失败，userId={}", userId, e);
            }
        }
        return success(new MealNotifyOverview(notifications, pendingCount, cookingCount));
    }

    /**
     * 读取当前登录用户的通知队列。
     *
     * @param role 队列角色，user 或 chef；身份 ID 始终取自登录态
     * @return 通知列表
     */
    @Operation(summary = "轮询订单通知", description = "读取并清空当前登录身份的通知队列，不信任客户端身份ID")
    @GetMapping("/poll")
    public AjaxResult poll(@RequestParam(defaultValue = ROLE_USER) String role) {
        Long userId = SecurityUtils.getUserId();
        if (ROLE_USER.equals(role)) {
            if (!SecurityUtils.hasRole(ROLE_MEAL_EATER) && !SecurityUtils.hasRole(ROLE_MEAL_CHEF)
                    && !SecurityUtils.hasRole(ROLE_MEAL_MANAGER)) {
                throw new ServiceException("没有权限读取点餐通知", HttpStatus.FORBIDDEN);
            }
            return success(mealNotifyService.poll("notify:user:" + userId));
        }
        if (ROLE_CHEF.equals(role)) {
            if (!SecurityUtils.hasRole(ROLE_MEAL_CHEF)) {
                throw new ServiceException("没有权限读取厨房通知", HttpStatus.FORBIDDEN);
            }
            return success(mealNotifyService.poll("notify:chef:" + userId));
        }
        throw new ServiceException("通知队列角色无效", HttpStatus.BAD_REQUEST);
    }
}
