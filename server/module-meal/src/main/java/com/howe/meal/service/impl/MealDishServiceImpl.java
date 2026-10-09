package com.howe.meal.service.impl;

import com.howe.common.exception.ServiceException;
import com.howe.common.utils.SecurityUtils;
import com.howe.meal.domain.MealCategoryRef;
import com.howe.meal.domain.MealDish;
import com.howe.meal.mapper.MealCategoryRefMapper;
import com.howe.meal.mapper.MealDishMapper;
import com.howe.meal.service.IMealDishService;
import com.howe.meal.util.MealScopeGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 点餐-菜品 服务层实现
 *
 * @author howe
 */
@Service
@RequiredArgsConstructor
public class MealDishServiceImpl implements IMealDishService {

    private final MealDishMapper mealDishMapper;
    private final MealCategoryRefMapper mealCategoryRefMapper;

    @Override
    public List<MealDish> selectMealDishList(MealDish mealDish) {
        if (mealDish.getDeptId() == null) {
            mealDish.setDeptId(SecurityUtils.getDeptId());
        }
        List<MealDish> list = mealDishMapper.selectMealDishList(mealDish);
        fillCategoryIds(list);
        return list;
    }

    /**
     * 菜品详情是「按 id 取详情」的典型场景，不受数据权限保护，
     * 必须在这里挡住跨家庭读取别人家的私有菜。
     */
    @Override
    public MealDish selectMealDishById(Long dishId) {
        MealDish dish = mealDishMapper.selectMealDishById(dishId);
        if (dish == null) {
            throw new ServiceException("菜品不存在或已删除");
        }
        boolean isPublic = dish.getDeptId() != null && dish.getDeptId().longValue() == 0L;
        if (!isPublic && !SecurityUtils.isAdmin()) {
            MealScopeGuard.assertSameDept(dish.getDeptId(), "菜品");
        }
        // 回填关联分类，供管理端编辑表单多选回显
        dish.setCategoryIds(mealCategoryRefMapper.selectCategoryIds(MealCategoryRef.BIZ_DISH, dishId));
        return dish;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertMealDish(MealDish mealDish) {
        if (mealDish.getDeptId() == null) {
            mealDish.setDeptId(SecurityUtils.getDeptId());
        }
        if (mealDish.getStatus() == null) {
            mealDish.setStatus("0");
        }
        if (mealDish.getSort() == null) {
            mealDish.setSort(0);
        }
        if (mealDish.getSource() == null) {
            mealDish.setSource("0");
        }
        mealDish.setCreateBy(SecurityUtils.getUsername());
        int rows = mealDishMapper.insertMealDish(mealDish);
        saveDishCategories(mealDish.getDishId(), mealDish.getCategoryIds());
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateMealDish(MealDish mealDish) {
        MealDish exist = mealDishMapper.selectMealDishById(mealDish.getDishId());
        if (exist == null) {
            throw new ServiceException("菜品不存在或已删除");
        }
        MealScopeGuard.assertWritable(exist.getDeptId(), "菜品");
        // 归属不允许通过修改接口迁移
        mealDish.setDeptId(null);
        mealDish.setUpdateBy(SecurityUtils.getUsername());
        int rows = mealDishMapper.updateMealDish(mealDish);
        // categoryIds 非空才视为「本次要改分类」；为 null 表示不动分类
        if (mealDish.getCategoryIds() != null) {
            mealCategoryRefMapper.deleteMealCategoryRefs(MealCategoryRef.BIZ_DISH, new Long[]{mealDish.getDishId()});
            saveDishCategories(mealDish.getDishId(), mealDish.getCategoryIds());
        }
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteMealDishByIds(Long[] dishIds) {
        if (dishIds == null || dishIds.length == 0) {
            throw new ServiceException("请选择要删除的数据");
        }
        for (Long dishId : dishIds) {
            MealDish exist = mealDishMapper.selectMealDishById(dishId);
            if (exist == null) {
                throw new ServiceException("菜品不存在或已删除");
            }
            MealScopeGuard.assertWritable(exist.getDeptId(), "菜品");
        }
        // 菜品逻辑删除，关联表按业务物理清理，避免残留引用挡住分类删除
        mealCategoryRefMapper.deleteMealCategoryRefs(MealCategoryRef.BIZ_DISH, dishIds);
        return mealDishMapper.deleteMealDishByIds(dishIds);
    }

    /** 列表接口批量回填 categoryIds：一次查全部关联，避免逐条查询 */
    private void fillCategoryIds(List<MealDish> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        List<Long> dishIds = new ArrayList<>(list.size());
        for (MealDish dish : list) {
            if (dish.getDishId() != null) {
                dishIds.add(dish.getDishId());
            }
        }
        if (dishIds.isEmpty()) {
            return;
        }
        Map<Long, List<Long>> grouped = new HashMap<>();
        for (MealCategoryRef ref : mealCategoryRefMapper.selectRefsByBizIds(MealCategoryRef.BIZ_DISH, dishIds)) {
            grouped.computeIfAbsent(ref.getBizId(), k -> new ArrayList<>()).add(ref.getCategoryId());
        }
        for (MealDish dish : list) {
            List<Long> ids = grouped.get(dish.getDishId());
            dish.setCategoryIds(ids != null ? ids : new ArrayList<>());
        }
    }

    /** 写入菜品的分类关联；categoryIds 为空则不做任何事 */
    private void saveDishCategories(Long dishId, List<Long> categoryIds) {
        if (dishId == null || categoryIds == null || categoryIds.isEmpty()) {
            return;
        }
        MealCategoryRef ref = new MealCategoryRef();
        ref.setBizType(MealCategoryRef.BIZ_DISH);
        ref.setBizId(dishId);
        ref.setCategoryIds(categoryIds);
        mealCategoryRefMapper.insertMealCategoryRefs(ref);
    }
}
