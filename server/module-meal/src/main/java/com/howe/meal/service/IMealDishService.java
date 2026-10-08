package com.howe.meal.service;

import com.howe.meal.domain.MealDish;

import java.util.List;

/**
 * 点餐-菜品 服务层
 *
 * @author howe
 */
public interface IMealDishService {
    /**
     * 查询菜品列表（公共菜谱 + 当前家庭的私有菜）
     *
     * @param mealDish 查询条件
     * @return 菜品集合
     */
    List<MealDish> selectMealDishList(MealDish mealDish);

    /**
     * 按主键查询菜品详情，并校验可见范围
     *
     * @param dishId 菜品ID
     * @return 菜品
     */
    MealDish selectMealDishById(Long dishId);

    /**
     * 新增菜品
     *
     * @param mealDish 菜品
     * @return 结果
     */
    int insertMealDish(MealDish mealDish);

    /**
     * 修改菜品
     *
     * @param mealDish 菜品
     * @return 结果
     */
    int updateMealDish(MealDish mealDish);

    /**
     * 批量删除菜品
     *
     * @param dishIds 菜品ID数组
     * @return 结果
     */
    int deleteMealDishByIds(Long[] dishIds);
}
