package com.howe.meal.service;

import com.howe.meal.domain.MealCategory;

import java.util.List;

/**
 * 点餐-菜品分类 服务层
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
     * 新增分类
     *
     * @param mealCategory 分类
     * @return 结果
     */
    int insertMealCategory(MealCategory mealCategory);

    /**
     * 修改分类
     *
     * @param mealCategory 分类
     * @return 结果
     */
    int updateMealCategory(MealCategory mealCategory);

    /**
     * 批量删除分类
     *
     * @param categoryIds 分类ID数组
     * @return 结果
     */
    int deleteMealCategoryByIds(Long[] categoryIds);
}
