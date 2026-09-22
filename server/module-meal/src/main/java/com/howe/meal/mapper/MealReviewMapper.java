package com.howe.meal.mapper;

import com.howe.meal.domain.MealReview;

import java.util.List;

/**
 * 点餐-评价 数据层
 *
 * <p>一单一评：{@code order_id} 上有唯一约束，本层另提供按订单查询，
 * 让 Service 能在插入前给出可读的提示，而不是抛数据库异常。</p>
 *
 * @author howe
 */
public interface MealReviewMapper {
    /**
     * 查询评价列表（配合 {@code @DataScope} 使用，管理端）
     *
     * @param mealReview 查询条件
     * @return 评价集合
     */
    List<MealReview> selectMealReviewList(MealReview mealReview);

    /**
     * 查询某个评价人自己的评价
     *
     * @param mealReview 查询条件，userId 必填
     * @return 评价集合
     */
    List<MealReview> selectMyReviewList(MealReview mealReview);

    /**
     * 按主键查询评价
     *
     * @param reviewId 评价ID
     * @return 评价
     */
    MealReview selectMealReviewById(Long reviewId);

    /**
     * 按订单查询评价，用于一单一评的前置校验
     *
     * @param orderId 订单ID
     * @return 评价，不存在返回 null
     */
    MealReview selectMealReviewByOrderId(Long orderId);

    /**
     * 查询指定订单集合中已被评价的订单ID
     *
     * @param orderIds 订单ID数组
     * @return 已评价的订单ID集合
     */
    List<Long> selectReviewedOrderIds(Long[] orderIds);

    /**
     * 新增评价
     *
     * @param mealReview 评价
     * @return 结果
     */
    int insertMealReview(MealReview mealReview);

    /**
     * 批量删除评价（逻辑删除）
     *
     * @param reviewIds 评价ID数组
     * @return 结果
     */
    int deleteMealReviewByIds(Long[] reviewIds);
}
