package com.howe.meal.service;

import com.howe.meal.domain.MealOrder;
import com.howe.meal.domain.dto.MealOrderSubmitBody;

import java.util.List;

/**
 * 点餐-订单 服务层
 *
 * @author howe
 */
public interface IMealOrderService {
    /**
     * 查询订单列表（管理端，受数据权限约束）
     *
     * @param mealOrder 查询条件
     * @return 订单集合
     */
    List<MealOrder> selectMealOrderList(MealOrder mealOrder);

    /**
     * 查询厨师工作台订单（受数据权限约束，附带点餐明细与等待/制作时长）
     *
     * @param mealOrder 查询条件
     * @return 订单集合
     */
    List<MealOrder> selectKitchenOrderList(MealOrder mealOrder);

    /**
     * 查询某个点餐人自己的订单
     *
     * @param mealOrder 查询条件
     * @return 订单集合
     */
    List<MealOrder> selectMyOrderList(MealOrder mealOrder);

    /**
     * 查询订单详情（含明细，校验归属）
     *
     * @param orderId 订单ID
     * @return 订单
     */
    MealOrder selectMealOrderDetail(Long orderId);

    /**
     * 下单
     *
     * @param body 下单入参
     * @return 新订单ID
     */
    Long submitOrder(MealOrderSubmitBody body);

    /**
     * 取消订单（限本人 + 未接单）
     *
     * @param orderId 订单ID
     * @return 结果
     */
    int cancelOrder(Long orderId);

    /**
     * 接单（待接单 → 制作中）
     *
     * @param orderId 订单ID
     * @return 结果
     */
    int acceptOrder(Long orderId);

    /**
     * 标记完成（制作中 → 已完成）
     *
     * @param orderId 订单ID
     * @return 结果
     */
    int finishOrder(Long orderId);

    /**
     * 批量删除订单
     *
     * @param orderIds 订单ID数组
     * @return 结果
     */
    int deleteMealOrderByIds(Long[] orderIds);
}
