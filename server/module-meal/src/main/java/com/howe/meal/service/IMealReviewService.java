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
     * 查询「我的评价」
     *
     * <p>默认返回当前点餐人自己写的评价；{@code scope="chef"} 时返回当前厨师
     * 接单制作过的订单所收到的评价。</p>
     *
     * @param mealReview 查询条件
     * @param scope      视角：空为点餐人，{@code "chef"} 为厨师
     * @return 评价集合
     */
    List<MealReview> selectMyReviewList(MealReview mealReview, String scope);

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
