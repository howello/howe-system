package com.howe.meal.tool.service;

import com.howe.meal.domain.MealCategory;
import com.howe.meal.domain.MealDish;
import com.howe.meal.domain.MealOrder;
import com.howe.meal.domain.MealOrderItem;
import com.howe.meal.domain.MealProposal;
import com.howe.meal.domain.MealReview;
import java.util.List;
import java.util.Map;

/**
 * 点餐只读工具的业务读取服务。
 *
 * <p>把「有效家庭预检 → 数据库分页主查询 → 有界投影」收在模块内部，
 * Provider 不再自行起分页或先查家庭表，避免分页线程状态与额外查询交错。
 * 本接口只读，不改动订单、候选、通知或缓存。</p>
 */
public interface IMealReadService {

    /**
     * 分页查询当前可见分类。
     *
     * @param page 页码
     * @param pageSize 每页条数
     * @param status 状态过滤，可为 null
     * @return 分页结果
     */
    PageResult<MealCategory> listCategories(int page, int pageSize, String status);

    /**
     * 按主键读取分类，保留原有公共/本家庭语义。
     *
     * @param categoryId 分类ID
     * @return 分类
     */
    MealCategory getCategory(Long categoryId);

    /**
     * 分页查询当前可见菜品。
     *
     * @param page 页码
     * @param pageSize 每页条数
     * @param name 菜名过滤，可为 null
     * @param categoryId 分类过滤，可为 null
     * @param status 状态过滤，可为 null
     * @return 分页结果
     */
    PageResult<MealDish> listDishes(int page, int pageSize, String name, Long categoryId, String status);

    /**
     * 按主键读取菜品，保留原有可见范围守卫。
     *
     * @param dishId 菜品ID
     * @return 菜品
     */
    MealDish getDish(Long dishId);

    /**
     * 读取招牌菜，最多 3 条。
     *
     * @return 招牌菜
     */
    List<MealDish> listSignatureDishes();

    /**
     * 分页查询本人订单。
     *
     * <p>日期区间以 {@link java.util.Date} 传入：MyBatis 对 {@code params} Map 中的
     * {@code LocalDateTime} 缺少可靠的 TypeHandler 解析，而 {@code Date} 有原生
     * {@code DateTypeHandler}，可稳定按 TIMESTAMP 绑定。</p>
     *
     * @param page 页码
     * @param pageSize 每页条数
     * @param status 状态过滤，可为 null
     * @param createdFrom 创建时间下界（含），可为 null
     * @param createdTo 创建时间上界（不含），可为 null
     * @return 分页结果
     */
    PageResult<MealOrder> listMyOrders(int page, int pageSize, String status, java.util.Date createdFrom,
            java.util.Date createdTo);

    /**
     * 读取本人可见的订单详情。
     *
     * @param orderId 订单ID
     * @return 订单
     */
    MealOrder getOrder(Long orderId);

    /**
     * 分页查询本人提案。
     *
     * @param page 页码
     * @param pageSize 每页条数
     * @param status 状态过滤，可为 null
     * @return 分页结果
     */
    PageResult<MealProposal> listMyProposals(int page, int pageSize, String status);

    /**
     * 读取家庭内可见的提案详情。
     *
     * @param proposalId 提案ID
     * @return 提案
     */
    MealProposal getProposal(Long proposalId);

    /**
     * 分页查询本人评价。
     *
     * @param page 页码
     * @param pageSize 每页条数
     * @return 分页结果
     */
    PageResult<MealReview> listMyReviews(int page, int pageSize);

    /**
     * 分页查询本家庭可指派厨师。
     *
     * @param page 页码
     * @param pageSize 每页条数
     * @return 分页结果
     */
    PageResult<com.howe.meal.domain.vo.MealOrderAssignee> listAssignees(int page, int pageSize);

    /**
     * 读取订单明细摘要，按明细主键稳定截断。
     *
     * @param orderId 订单ID
     * @param limit 最多返回条数
     * @return 明细摘要
     */
    List<MealOrderItem> listOrderItemSummaries(Long orderId, int limit);

    /**
     * 批量读取订单明细摘要。
     *
     * @param orderIds 订单ID数组
     * @param limitPerOrder 每单最多返回条数，由 SQL 按订单分区限量
     * @return 每单有界明细摘要
     */
    List<MealOrderItem> listOrderItemSummaries(Long[] orderIds, int limitPerOrder);

    /**
     * 统计订单明细总数。
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
    List<Map<String, Object>> countOrderItems(Long[] orderIds);

    /**
     * 通用分页结果。
     *
     * @param items 当前页数据
     * @param total 符合条件的总数
     * @param hasMore 是否还有下一页
     * @param <T> 元素类型
     */
    record PageResult<T>(List<T> items, long total, boolean hasMore) {
    }
}
