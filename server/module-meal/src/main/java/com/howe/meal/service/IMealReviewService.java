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
     * 接单制作过的订单所收到的评价。结果附带订单信息与菜品快照。</p>
     *
     * @param mealReview 查询条件
     * @param scope      视角：空为点餐人，{@code "chef"} 为厨师
     * @return 评价集合
     */
    List<MealReview> selectMyReviewList(MealReview mealReview, String scope);

    /**
     * 工具专用的本人评价分页查询。
     *
     * <p>分页由调用方在主查询前后控制：本方法只执行评价主查询，返回后立刻清理
     * PageHelper 上下文，再补菜品快照。否则补数据的查询会被 PageHelper
     * 当作分页目标。</p>
     *
     * <p>调用方必须先 {@code PageHelper.startPage(page, pageSize)}。</p>
     *
     * @param mealReview 查询条件，userId 由本方法强制写入
     * @return 当前页评价（已补齐菜品快照）
     */
    List<MealReview> selectMyReviewPageForTool(MealReview mealReview);

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
