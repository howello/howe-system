package com.howe.meal.service.impl;

import com.howe.common.exception.ServiceException;
import com.howe.common.utils.SecurityUtils;
import com.howe.meal.domain.MealDish;
import com.howe.meal.mapper.MealDishMapper;
import com.howe.meal.service.IMealDishService;
import com.howe.meal.util.MealScopeGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 点餐-菜品 服务层实现
 *
 * @author howe
 */
@Service
@RequiredArgsConstructor
public class MealDishServiceImpl implements IMealDishService {

    private final MealDishMapper mealDishMapper;

    @Override
    public List<MealDish> selectMealDishList(MealDish mealDish) {
        if (mealDish.getDeptId() == null) {
            mealDish.setDeptId(SecurityUtils.getDeptId());
        }
        return mealDishMapper.selectMealDishList(mealDish);
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
        return dish;
    }

    @Override
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
        return mealDishMapper.insertMealDish(mealDish);
    }

    @Override
    public int updateMealDish(MealDish mealDish) {
        MealDish exist = mealDishMapper.selectMealDishById(mealDish.getDishId());
        if (exist == null) {
            throw new ServiceException("菜品不存在或已删除");
        }
        MealScopeGuard.assertWritable(exist.getDeptId(), "菜品");
        // 归属不允许通过修改接口迁移
        mealDish.setDeptId(null);
        mealDish.setUpdateBy(SecurityUtils.getUsername());
        return mealDishMapper.updateMealDish(mealDish);
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
        return mealDishMapper.deleteMealDishByIds(dishIds);
    }
}
