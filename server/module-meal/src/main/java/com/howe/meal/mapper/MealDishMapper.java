package com.howe.meal.mapper;

import com.howe.meal.domain.MealDish;

import java.util.List;

/**
 * 点餐-菜品 数据层
 *
 * <p>与分类同理：{@code dept_id = 0} 是公共菜谱，所有家庭可见，
 * 因此<b>不走 {@code @DataScope}</b>，隔离条件在 XML 里手写。</p>
 *
 * @author howe
 */
public interface MealDishMapper {
    /**
     * 查询菜品列表（公共菜谱 + 指定家庭的私有菜）
     *
     * @param mealDish 查询条件，deptId 为当前登录用户所属家庭
     * @return 菜品集合
     */
    List<MealDish> selectMealDishList(MealDish mealDish);

    /**
     * 按主键查询菜品
     *
     * @param dishId 菜品ID
     * @return 菜品
     */
    MealDish selectMealDishById(Long dishId);

    /**
     * 查询审核范围内的菜品名称（本家庭私有菜 + 公共菜谱，含已下架）
     *
     * <p>供新菜提案 AI 自动审核判断是否为已有菜品，避免同一道菜重复上架。</p>
     *
     * @param deptId 归属家庭ID（0 表示只查公共菜谱）
     * @return 菜品名称集合
     */
    List<String> selectDishNamesByDept(Long deptId);

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
     * 批量删除菜品（逻辑删除）
     *
     * @param dishIds 菜品ID数组
     * @return 结果
     */
    int deleteMealDishByIds(Long[] dishIds);
}
