package com.howe.meal.service.impl;

import com.howe.common.exception.ServiceException;
import com.howe.common.utils.SecurityUtils;
import com.howe.meal.domain.MealCategory;
import com.howe.meal.mapper.MealCategoryMapper;
import com.howe.meal.service.IMealCategoryService;
import com.howe.meal.util.MealScopeGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 点餐-菜品分类 服务层实现
 *
 * @author howe
 */
@Service
@RequiredArgsConstructor
public class MealCategoryServiceImpl implements IMealCategoryService {

    private final MealCategoryMapper mealCategoryMapper;

    @Override
    public List<MealCategory> selectMealCategoryList(MealCategory mealCategory) {
        // 不传 deptId 时默认按当前家庭过滤，超管可显式传 0 只看公共分类
        if (mealCategory.getDeptId() == null) {
            mealCategory.setDeptId(SecurityUtils.getDeptId());
        }
        return mealCategoryMapper.selectMealCategoryList(mealCategory);
    }

    /**
     * 分类详情同样是「按 id 取详情」，不受数据权限保护：
     * 公共分类（dept_id = 0）谁都能看，家庭私有分类只有本家庭与平台管理员能看。
     */
    @Override
    public MealCategory selectMealCategoryById(Long categoryId) {
        MealCategory category = mealCategoryMapper.selectMealCategoryById(categoryId);
        if (category == null) {
            throw new ServiceException("分类不存在");
        }
        boolean isPublic = category.getDeptId() != null && category.getDeptId().longValue() == 0L;
        if (!isPublic && !SecurityUtils.isAdmin()) {
            MealScopeGuard.assertSameDept(category.getDeptId(), "分类");
        }
        return category;
    }

    @Override
    public int insertMealCategory(MealCategory mealCategory) {
        if (mealCategory.getDeptId() == null) {
            mealCategory.setDeptId(SecurityUtils.getDeptId());
        }
        if (mealCategory.getStatus() == null) {
            mealCategory.setStatus("0");
        }
        if (mealCategory.getSort() == null) {
            mealCategory.setSort(0);
        }
        mealCategory.setCreateBy(SecurityUtils.getUsername());
        return mealCategoryMapper.insertMealCategory(mealCategory);
    }

    @Override
    public int updateMealCategory(MealCategory mealCategory) {
        MealCategory exist = mealCategoryMapper.selectMealCategoryById(mealCategory.getCategoryId());
        if (exist == null) {
            throw new ServiceException("分类不存在");
        }
        MealScopeGuard.assertWritable(exist.getDeptId(), "分类");
        // 归属不允许通过修改接口迁移
        mealCategory.setDeptId(null);
        mealCategory.setUpdateBy(SecurityUtils.getUsername());
        return mealCategoryMapper.updateMealCategory(mealCategory);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteMealCategoryByIds(Long[] categoryIds) {
        if (categoryIds == null || categoryIds.length == 0) {
            throw new ServiceException("请选择要删除的数据");
        }
        for (Long categoryId : categoryIds) {
            MealCategory exist = mealCategoryMapper.selectMealCategoryById(categoryId);
            if (exist == null) {
                throw new ServiceException("分类不存在");
            }
            MealScopeGuard.assertWritable(exist.getDeptId(), "分类");
            if (mealCategoryMapper.countDishByCategoryId(categoryId) > 0) {
                throw new ServiceException("分类「" + exist.getName() + "」下还有菜品，不能删除");
            }
        }
        return mealCategoryMapper.deleteMealCategoryByIds(categoryIds);
    }
}
