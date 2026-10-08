package com.howe.meal.mapper;

import com.howe.meal.domain.MealCategory;

import java.util.List;

/**
 * 点餐-菜品分类 数据层
 *
 * <p>本表承载「公共分类 + 家庭私有分类」，因此<b>不走 {@code @DataScope}</b>：
 * 数据权限只会拼出「本部门」，会把 {@code dept_id = 0} 的公共分类一起过滤掉。
 * 隔离条件在 XML 里手写，调用方通过 {@code deptId} 传当前家庭。</p>
 *
 * @author howe
 */
public interface MealCategoryMapper {
    /**
     * 查询分类列表（公共分类 + 指定家庭的私有分类）
     *
     * @param mealCategory 查询条件，deptId 为当前登录用户所属家庭
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
     * 批量删除分类（逻辑删除）
     *
     * @param categoryIds 分类ID数组
     * @return 结果
     */
    int deleteMealCategoryByIds(Long[] categoryIds);

    /**
     * 统计分类下的在用菜品数，删除前校验
     *
     * @param categoryId 分类ID
     * @return 菜品数
     */
    int countDishByCategoryId(Long categoryId);
}
