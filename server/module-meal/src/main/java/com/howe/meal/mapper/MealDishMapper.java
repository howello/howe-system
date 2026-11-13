package com.howe.meal.mapper;

import com.howe.meal.domain.MealDish;
import org.apache.ibatis.annotations.Param;

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
     * 在指定可见范围内按多个菜名词项查找已上架菜品。
     *
     * @param keywords 菜名查询词或 AI 识别出的同义词
     * @param deptId   当前家庭 ID；平台管理员传 null 表示沿用全量可见范围
     * @param limit    最大返回条数
     * @return 菜品候选列表
     */
    List<MealDish> selectMealDishSearchCandidates(@Param("keywords") List<String> keywords,
                                                   @Param("deptId") Long deptId,
                                                   @Param("limit") int limit);

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

    /**
     * 招牌推荐位：本家庭窗口内已完成订单的菜品按份数排序，只取仍上架的菜
     *
     * @param deptId   家庭ID；null 表示不限（平台管理员）
     * @param startDay 起始日期 yyyy-MM-dd（含）
     * @param endDay   截止日期 yyyy-MM-dd（含）
     * @param limit    取前 N 条
     * @return 菜品列表（附带 orderCount）
     */
    List<MealDish> selectSignatureDishes(@Param("deptId") Long deptId,
                                         @Param("startDay") String startDay,
                                         @Param("endDay") String endDay,
                                         @Param("limit") int limit);

    /**
     * 条件更新菜品归属
     *
     * <p>where 里带旧归属：并发迁移时，先到先得，后到的请求因旧值不匹配而影响 0 行，
     * 由调用方提示刷新，不会出现后到者无感知覆盖先到者。</p>
     *
     * @param dishId        菜品 ID
     * @param expectedDeptId 旧归属（并发校验）
     * @param targetDeptId  新归属；0 表示平台公共菜谱
     * @param updateBy      更新人
     * @return 影响行数
     */
    int updateDishOwner(@Param("dishId") Long dishId,
                        @Param("expectedDeptId") Long expectedDeptId,
                        @Param("targetDeptId") Long targetDeptId,
                        @Param("updateBy") String updateBy);

    /**
     * 判断菜品是否能出现在点餐端菜单
     *
     * <p>条件与列表的 {@code menuVisible} 一致：所属分类未停用、未删除；
     * 未绑定分类的菜仍视为可见。用于下单时的最终校验。</p>
     *
     * @param dishId 菜品 ID
     * @return 可见返回 true
     */
    boolean isDishMenuVisible(@Param("dishId") Long dishId);
}
