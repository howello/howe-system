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
     * 工具专用的厨师工作台分页主查询。
     *
     * <p>与 {@link #selectMyOrderPageForTool(MealOrder)} 同理：分页由调用方启动，
     * 本方法在主查询后立即清理 PageHelper，只补时长，不加载无界明细；
     * 明细由调用方用每单固定上限的摘要查询补齐。</p>
     *
     * @param mealOrder 查询条件，status 必须为待接单或制作中
     * @return 当前页订单（不含明细列表）
     */
    List<MealOrder> selectKitchenOrderPageForTool(MealOrder mealOrder);

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
     * 工具专用的本人订单分页主查询。
     *
     * <p>分页由调用方在主查询前后控制；本方法在主查询后立即清理 PageHelper，
     * 只补已评价标记与时长，不加载订单明细。工具列表明细由独立 SQL 窗口查询按每单固定上限读取。</p>
     *
     * <p>与 {@link #selectMyOrderList(MealOrder, String)} 的 Web 路径分离，保留旧 Web 列表的完整明细契约，
     * 避免补数据的额外查询在分页上下文内执行。</p>
     *
     * @param mealOrder 查询条件，userId 由本方法强制写入
     * @return 当前页订单（不含明细列表）
     */
    List<MealOrder> selectMyOrderPageForTool(MealOrder mealOrder);

    /**
     * 查询订单详情（含明细，校验归属）
     *
     * @param orderId 订单ID
     * @return 订单
     */
    MealOrder selectMealOrderDetail(Long orderId);

    /**
     * 工具专用的订单主记录详情读取。
     *
     * <p>只加载订单主记录，保留家庭归属守卫、已评价标记与时长计算；不加载明细。
     * 工具随后通过有界摘要查询单独获取最多指定条数的明细，避免调用既有详情方法
     * 无界读取全部 {@code meal_order_item}。</p>
     *
     * @param orderId 订单ID
     * @return 订单主记录
     */
    MealOrder selectMealOrderForTool(Long orderId);

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
     * 工具专用的取消：在 Web 路径守卫之上，再要求订单数据版本等于预览时的版本。
     *
     * <p>仅当 {@code expectedVersion != null} 时启用版本条件；Web 路径继续调用
     * {@link #cancelOrder(Long)}，行为不变。</p>
     *
     * @param orderId 订单ID
     * @param expectedVersion 期望的订单数据版本
     * @return 结果
     */
    int cancelOrder(Long orderId, Long expectedVersion);

    /**
     * 接单（待接单 → 制作中）
     *
     * @param orderId 订单ID
     * @return 结果
     */
    int acceptOrder(Long orderId);

    /**
     * 工具专用的接单：要求订单版本与本人候选版本/轮次均匹配预览快照。
     *
     * @param orderId 订单ID
     * @param expectedVersion 期望的订单数据版本
     * @param candidateVersion 期望的本人候选数据版本
     * @param candidateRound 期望的候选轮次
     * @return 结果
     */
    int acceptOrder(Long orderId, Long expectedVersion, Long candidateVersion, Long candidateRound);

    /**
     * 候选人拒绝订单（指派单或公开单均可用）
     *
     * @param orderId 订单ID
     */
    void refuseOrder(Long orderId);

    /**
     * 工具专用的拒单：要求订单版本与本人候选版本/轮次均匹配预览快照。
     *
     * @param orderId 订单ID
     * @param expectedVersion 期望的订单数据版本
     * @param candidateVersion 期望的本人候选数据版本
     * @param candidateRound 期望的候选轮次
     */
    void refuseOrder(Long orderId, Long expectedVersion, Long candidateVersion, Long candidateRound);

    /**
     * 查询本家庭可指派的厨师，供确认下单页的「指派厨师」选择器使用
     *
     * @return 可指派厨师列表
     */
    List<MealOrderAssignee> selectAssignees();

    /**
     * 工具专用的可指派厨师分页查询。
     *
     * <p>有效家庭校验在分页之前完成，主查询直接对厨师单表做数据库分页；
     * 返回字段只有用户 ID 与展示名。</p>
     *
     * @param page 页码，从 1 开始
     * @param pageSize 每页条数
     * @return 当前页厨师
     */
    List<MealOrderAssignee> selectAssigneesForTool(int page, int pageSize);

    /**
     * 工具专用的本人订单明细摘要。
     *
     * <p>只查询指定订单并返回按 {@code item_id} 稳定排序的前 {@code limit} 项，
     * 不加载备注等非白名单字段。</p>
     *
     * @param orderId 订单ID
     * @param limit 最多返回条数
     * @return 明细摘要
     */
    List<com.howe.meal.domain.MealOrderItem> selectOrderItemSummaries(Long orderId, int limit);

    /**
     * 批量查询工具专用的订单明细摘要。
     *
     * @param orderIds 订单ID数组
     * @param limitPerOrder 每单最多返回条数
     * @return 每单最多 {@code limitPerOrder} 条摘要
     */
    List<com.howe.meal.domain.MealOrderItem> selectOrderItemSummaries(Long[] orderIds, int limitPerOrder);

    /**
     * 统计订单明细总数，用于判断摘要是否被截断。
     *
     * @param orderId 订单ID
     * @return 明细总数
     */
    int countOrderItems(Long orderId);

    /**
     * 批量统计订单明细总数。
     *
     * @param orderIds 订单ID数组
     * @return 每行含 {@code orderId} 与 {@code cnt}
     */
    List<java.util.Map<String, Object>> countOrderItems(Long[] orderIds);

    /**
     * 标记完成（制作中 → 已完成）
     *
     * @param orderId 订单ID
     * @return 结果
     */
    int finishOrder(Long orderId);

    /**
     * 工具专用的完成：要求订单数据版本等于预览时的版本。
     *
     * @param orderId 订单ID
     * @param expectedVersion 期望的订单数据版本
     * @return 结果
     */
    int finishOrder(Long orderId, Long expectedVersion);

    /**
     * 批量删除订单
     *
     * @param orderIds 订单ID数组
     * @return 结果
     */
    int deleteMealOrderByIds(Long[] orderIds);
}
