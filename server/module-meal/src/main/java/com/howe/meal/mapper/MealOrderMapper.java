package com.howe.meal.mapper;

import com.howe.meal.domain.MealOrder;
import com.howe.meal.domain.MealOrderItem;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 点餐-订单 数据层
 *
 * <p>订单是纯家庭私有数据：list 查询（管理端、厨师端）走 {@code @DataScope}，
 * 点餐人的「我的订单」另有独立方法强制按 {@code user_id} 过滤。
 * 「按 id 取详情」不受数据权限保护，归属校验在 Service 层完成。</p>
 *
 * @author howe
 */
public interface MealOrderMapper {
    /**
     * 查询订单列表（配合 {@code @DataScope} 使用，厨师端与管理端）
     *
     * @param mealOrder 查询条件
     * @return 订单集合
     */
    List<MealOrder> selectMealOrderList(MealOrder mealOrder);

    /**
     * 查询某个点餐人自己的订单
     *
     * @param mealOrder 查询条件，userId 必填
     * @return 订单集合
     */
    List<MealOrder> selectMyOrderList(MealOrder mealOrder);

    /**
     * 按主键查询订单
     *
     * @param orderId 订单ID
     * @return 订单
     */
    MealOrder selectMealOrderById(Long orderId);

    /**
     * 查询订单明细
     *
     * @param orderId 订单ID
     * @return 明细集合
     */
    List<MealOrderItem> selectItemsByOrderId(Long orderId);

    /**
     * 批量查询多个订单的明细
     *
     * @param orderIds 订单ID数组
     * @return 明细集合
     */
    List<MealOrderItem> selectItemsByOrderIds(Long[] orderIds);

    /**
     * 新增订单
     *
     * @param mealOrder 订单
     * @return 结果
     */
    int insertMealOrder(MealOrder mealOrder);

    /**
     * 批量新增订单明细
     *
     * @param items 明细集合
     * @return 结果
     */
    int insertOrderItems(@Param("items") List<MealOrderItem> items);

    /**
     * 状态流转（接单 / 完成 / 取消），只更新状态相关字段
     *
     * @param mealOrder 订单，orderId 与 status 必填
     * @return 结果
     */
    int updateMealOrderStatus(MealOrder mealOrder);

    /**
     * 批量删除订单（逻辑删除）
     *
     * @param orderIds 订单ID数组
     * @return 结果
     */
    int deleteMealOrderByIds(Long[] orderIds);

    /**
     * 查询当天已生成的最大订单号，用于生成下一个流水号
     *
     * @param prefix 订单号前缀（形如 M20260922）
     * @return 最大订单号，当天没有订单时返回 null
     */
    String selectMaxOrderNo(@Param("prefix") String prefix);
}
