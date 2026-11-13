package com.howe.meal.service;

import com.howe.meal.domain.dto.MealCategoryReviewResult;
import com.howe.meal.domain.dto.MealCategorySaveRequest;
import com.howe.meal.domain.dto.MealCategorySaveResult;

/**
 * 点餐分类 AI 审阅
 *
 * <p>新增/修改分类时，先由 AI 审阅名称、说明与依据，通过后保存润色值；
 * 不通过时返回原因与修改示例；AI 技术不可用时降级放行并标记「未经审阅」。</p>
 *
 * @author howe
 */
public interface IMealCategoryReviewService {

    /**
     * 审阅一次分类保存请求
     *
     * @param request    用户提交的分类内容
     * @param operation  操作类型：CREATE / UPDATE
     * @param categoryId 修改时的分类 ID；新增为 null
     * @param ownerDeptId 分类归属家庭 ID；0 表示公共分类
     * @return 审阅结果；AI 不可用时返回 {@code null}，由调用方降级放行
     */
    MealCategoryReviewResult review(MealCategorySaveRequest request, String operation,
            Long categoryId, Long ownerDeptId);
}
