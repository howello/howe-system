package com.howe.meal.controller;

import com.howe.common.core.controller.BaseController;
import com.howe.common.core.domain.AjaxResult;
import com.howe.common.constant.HttpStatus;
import com.howe.common.exception.ServiceException;
import com.howe.common.utils.SecurityUtils;
import com.howe.meal.domain.MealNotification;
import com.howe.meal.service.IMealNotifyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 点餐订单通知控制层。
 */
@Tag(name = "点餐-通知", description = "轮询当前登录用户的订单通知")
@RestController
@RequestMapping("/meal/notify")
@RequiredArgsConstructor
public class MealNotifyController extends BaseController {

    private static final String ROLE_USER = "user";
    private static final String ROLE_CHEF = "chef";
    private final IMealNotifyService mealNotifyService;

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
            if (!SecurityUtils.hasRole("meal_eater") && !SecurityUtils.hasRole("meal_chef")
                    && !SecurityUtils.hasRole("meal_manager")) {
                throw new ServiceException("没有权限读取点餐通知", HttpStatus.FORBIDDEN);
            }
            return success(mealNotifyService.poll("notify:user:" + userId));
        }
        if (ROLE_CHEF.equals(role)) {
            if (!SecurityUtils.hasRole("meal_chef") && !SecurityUtils.hasRole("meal_manager")) {
                throw new ServiceException("没有权限读取厨房通知", HttpStatus.FORBIDDEN);
            }
            return success(mealNotifyService.poll("notify:chef:" + userId));
        }
        throw new ServiceException("通知队列角色无效", HttpStatus.BAD_REQUEST);
    }
}
