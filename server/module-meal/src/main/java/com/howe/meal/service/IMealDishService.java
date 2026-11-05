package com.howe.meal.service;

import com.howe.meal.domain.MealDish;
import com.howe.meal.domain.dto.DishOwnerChangeRequest;
import com.howe.meal.domain.vo.DishOwnerOptionVO;

import java.util.List;

/**
 * 点餐-菜品 服务层
 *
 * <p>分类是唯一主类：菜品最多绑定 1 个分类；未选主类允许保存并标记「待分类」。</p>
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

    /**
     * 招牌推荐位：本家庭近 90 天已完成订单中按份数取前 3 道仍上架的菜
     *
     * @return 按份数倒序的招牌菜品
     */
    List<MealDish> selectSignatureDishes();

    /**
     * 查询「调整归属」可选的家庭
     *
     * <p>只返回 {@code dept_type='F'}、未删除、启用的家庭，最多 50 条。</p>
     *
     * @param keyword 家庭名称模糊搜索词，可空
     * @return 家庭选项集合
     */
    List<DishOwnerOptionVO> selectOwnerOptions(String keyword);

    /**
     * 调整菜品归属（仅平台管理员）
     *
     * <p>目标只能是 {@code 0}（平台公共菜谱）或有效家庭。带旧归属做并发校验：
     * 期间有其他人迁移过，本次请求按过期处理并提示刷新。</p>
     *
     * @param dishId  菜品 ID
     * @param request 调整请求
     * @return 影响行数
     */
    int changeDishOwner(Long dishId, DishOwnerChangeRequest request);
}
