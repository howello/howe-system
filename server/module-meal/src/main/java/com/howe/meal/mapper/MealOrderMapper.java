package com.howe.meal.mapper;

import com.howe.meal.domain.MealOrder;
import com.howe.meal.domain.MealOrderItem;
import com.howe.meal.domain.vo.MealOrderAssignee;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;
import java.util.Map;

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
     * 查询某个厨师自己接单制作过的订单
     *
     * @param mealOrder 查询条件，acceptUserId 必填
     * @return 订单集合
     */
    List<MealOrder> selectCookedOrderList(MealOrder mealOrder);

    /**
     * 按状态统计厨师工作台待办订单
     *
     * <p>配合 {@code @DataScope} 使用，只返回待接单与制作中两种状态的订单数，
     * 不受工作台列表分页上限影响。</p>
     *
     * @param mealOrder 查询条件，数据范围由 {@code @DataScope} 注入
     * @return 每行含 status 与 cnt
     */
    List<Map<String, Object>> selectKitchenOrderStatusCounts(MealOrder mealOrder);

    /**
     * 按主键查询订单
     *
     * @param orderId 订单ID
     * @return 订单
     */
    MealOrder selectMealOrderById(Long orderId);

    /**
     * 查询具有厨师角色的家庭成员用户ID。
     *
     * @param deptId 所属家庭ID
     * @return 厨师用户ID集合
     */
    List<Long> selectChefUserIdsByDeptId(@Param("deptId") Long deptId);

    /**
     * 查询本家庭可指派的厨师（含展示名），供确认下单页的选择器使用
     *
     * @param deptId 所属家庭ID
     * @return 可指派厨师列表
     */
    List<MealOrderAssignee> selectChefAssigneesByDeptId(@Param("deptId") Long deptId);

    /**
     * 按用户查询本家庭厨师信息，用于下单时校验被指派人资格
     *
     * @param deptId 所属家庭ID
     * @param userId 被指派的用户ID
     * @return 厨师信息，不具备资格时返回 {@code null}
     */
    MealOrderAssignee selectChefUserInfoByDeptId(@Param("deptId") Long deptId, @Param("userId") Long userId);

    /**
     * 查询某订单并锁定该行，供拒绝流程串行化使用
     *
     * @param orderId 订单ID
     * @return 订单
     */
    MealOrder selectOrderForUpdate(@Param("orderId") Long orderId);

    /**
     * 查询已过接单截止时间但仍待接单的订单ID
     *
     * @param limit 单批上限
     * @return 订单ID列表
     */
    List<Long> selectExpiredWaitingOrderIds(@Param("limit") int limit);

    /**
     * 查询仍待接单、但已没有任何待响应候选人的订单ID
     *
     * <p>用于全员拒绝的定时兜底：正常情况下最后一名候选人拒绝时订单已即时关闭，
     * 这里只捕获并发或异常留下的悬挂订单。</p>
     *
     * @param limit 单批上限
     * @return 订单ID列表
     */
    List<Long> selectWaitingWithoutPendingCandidate(@Param("limit") int limit);

    /**
     * 指派转公开：修改指派模式并重置接单截止时间
     *
     * @param orderId      订单ID
     * @param deadlineTime 新的截止时间
     * @return 影响行数，0 表示订单已不在指派中
     */
    int reopenToPublic(@Param("orderId") Long orderId, @Param("deadlineTime") Date deadlineTime);

    /**
     * 查询具有家庭管理员角色的家庭成员用户ID。
     *
     * @param deptId 所属家庭ID
     * @return 管理员用户ID集合
     */
    List<Long> selectManagerUserIdsByDeptId(@Param("deptId") Long deptId);

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
