package com.howe.meal.service;

import com.howe.meal.domain.MealCategory;
import com.howe.meal.domain.dto.MealCategorySaveRequest;
import com.howe.meal.domain.dto.MealCategorySaveResult;

import java.util.List;

/**
 * 点餐-菜品分类 服务层
 *
 * <p>公共分类（{@code deptId = 0}）固定 9 条，名称与数量锁定，只允许平台管理员
 * 修改说明、依据、图标、排序、状态；家庭私有分类由家庭管理员与平台超管维护，
 * 可新增、修改、删除。</p>
 *
 * @author howe
 */
public interface IMealCategoryService {

    /**
     * 查询分类列表（公共分类 + 当前家庭的私有分类）
     *
     * @param mealCategory 查询条件
     * @return 分类集合
     */
    List<MealCategory> selectMealCategoryList(MealCategory mealCategory);

    /**
     * 按主键查询分类
     *
     * @param categoryId 分类ID
     * @return 分类
     */
    MealCategory selectMealCategoryById(Long categoryId);

    /**
     * 新增家庭私有分类（经 AI 审阅后保存）
     *
     * @param request 分类保存请求
     * @return 保存结果（含审阅结论）
     */
    MealCategorySaveResult insertMealCategory(MealCategorySaveRequest request);

    /**
     * 修改分类（经 AI 审阅后保存）
     *
     * @param categoryId 分类 ID
     * @param request    分类保存请求
     * @return 保存结果（含审阅结论）
     */
    MealCategorySaveResult updateMealCategory(Long categoryId, MealCategorySaveRequest request);

    /**
     * 批量删除家庭私有分类
     *
     * <p>公共分类不可删除；分类下仍有菜品或提案引用时整批拒绝。</p>
     *
     * @param categoryIds 分类ID数组
     * @return 影响行数
     */
    int deleteMealCategoryByIds(Long[] categoryIds);
}
