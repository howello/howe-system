package com.howe.meal.service;

import com.howe.meal.domain.MealCategory;

import java.util.List;

/**
 * 点餐-菜品分类 服务层
 *
 * <p>分类是固定 9 条的唯一主类：新增与删除不开放，修改仅平台管理员可用。</p>
 *
 * @author howe
 */
public interface IMealCategoryService {
    /**
     * 查询分类列表（公共主类 + 当前家庭的私有分类）
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
     * 新增分类：不开放，始终抛错
     *
     * @param mealCategory 分类
     * @return 结果
     */
    int insertMealCategory(MealCategory mealCategory);

    /**
     * 修改分类（仅平台管理员，且不能改名）
     *
     * @param mealCategory 分类
     * @return 结果
     */
    int updateMealCategory(MealCategory mealCategory);

    /**
     * 批量删除分类：不开放，始终抛错
     *
     * @param categoryIds 分类ID数组
     * @return 结果
     */
    int deleteMealCategoryByIds(Long[] categoryIds);
}
