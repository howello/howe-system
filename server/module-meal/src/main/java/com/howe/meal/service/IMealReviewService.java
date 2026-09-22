package com.howe.meal.service;

import com.howe.meal.domain.MealReview;

import java.util.List;

/**
 * 点餐-评价 服务层
 *
 * @author howe
 */
public interface IMealReviewService {
    /**
     * 查询评价列表（管理端，受数据权限约束）
     *
     * @param mealReview 查询条件
     * @return 评价集合
     */
    List<MealReview> selectMealReviewList(MealReview mealReview);

    /**
     * 查询某个评价人自己的评价
     *
     * @param mealReview 查询条件
     * @return 评价集合
     */
    List<MealReview> selectMyReviewList(MealReview mealReview);

    /**
     * 提交评价（限本人已完成且未评价的订单）
     *
     * @param mealReview 评价
     * @return 结果
     */
    int submitReview(MealReview mealReview);

    /**
     * 批量删除评价
     *
     * @param reviewIds 评价ID数组
     * @return 结果
     */
    int deleteMealReviewByIds(Long[] reviewIds);
}
