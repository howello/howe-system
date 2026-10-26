package com.howe.meal.service;

import com.howe.meal.domain.MealOrder;
import com.howe.meal.domain.vo.MealOrderAssignee;
import com.howe.meal.domain.dto.MealOrderSubmitBody;
import com.howe.meal.domain.vo.MealKitchenOrderSummary;

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
     * 统计厨师工作台待办订单（受数据权限约束）
     *
     * @param mealOrder 查询条件
     * @return 待接单与制作中订单数
     */
    MealKitchenOrderSummary countKitchenOrderSummary(MealOrder mealOrder);

    /**
     * 查询「我的订单」
     *
     * <p>默认返回当前点餐人自己下的订单；{@code scope="chef"} 时返回当前厨师自己接单制作过的订单。</p>
     *
     * @param mealOrder 查询条件
     * @param scope     视角：空为点餐人，{@code "chef"} 为厨师
     * @return 订单集合
     */
    List<MealOrder> selectMyOrderList(MealOrder mealOrder, String scope);

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
     * 候选人拒绝订单（指派单或公开单均可用）
     *
     * @param orderId 订单ID
     */
    void refuseOrder(Long orderId);

    /**
     * 查询本家庭可指派的厨师，供确认下单页的「指派厨师」选择器使用
     *
     * @return 可指派厨师列表
     */
    List<MealOrderAssignee> selectAssignees();

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
