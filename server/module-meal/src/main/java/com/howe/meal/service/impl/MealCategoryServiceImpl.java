package com.howe.meal.service.impl;

import com.howe.common.exception.ServiceException;
import com.howe.common.utils.SecurityUtils;
import com.howe.meal.constant.MealCategoryTaxonomy;
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
 * <p>分类是固定 9 条的唯一主类：<b>不开放新增与删除</b>，修改也只允许平台管理员，
 * 且不允许改名称与归属。这样 AI 候选、迁移映射与点餐端分类栏始终稳定。</p>
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
        if (!isPublic) {
            MealScopeGuard.assertSameDept(category.getDeptId(), "分类");
        }
        return category;
    }

    /**
     * 分类新增不开放：分类表固定 9 条主类，避免唯一主类体系被稀释
     *
     * @param mealCategory 分类
     * @return 永不返回，始终抛错
     */
    @Override
    public int insertMealCategory(MealCategory mealCategory) {
        throw new ServiceException("分类固定为 9 个唯一主类，不支持新增");
    }

    /**
     * 分类修改：仅平台管理员可控，且只能改说明、依据、图标、排序、状态与备注
     *
     * @param mealCategory 分类
     * @return 结果
     */
    @Override
    public int updateMealCategory(MealCategory mealCategory) {
        if (!SecurityUtils.isAdmin()) {
            throw new ServiceException("只有平台管理员可以维护分类说明");
        }
        MealCategory exist = mealCategoryMapper.selectMealCategoryById(mealCategory.getCategoryId());
        if (exist == null) {
            throw new ServiceException("分类不存在");
        }
        // 名称是唯一主类的稳定标识，不允许改名；归属也不允许迁移
        if (mealCategory.getName() != null && !mealCategory.getName().trim().equals(exist.getName())) {
            throw new ServiceException("分类名称固定，不支持修改");
        }
        if (MealCategoryTaxonomy.isSignature(mealCategory.getName())) {
            throw new ServiceException("「招牌」不是分类，不能写入分类表");
        }
        if (!MealCategoryTaxonomy.isPrimaryCategory(exist.getName())) {
            throw new ServiceException("只能维护 9 个唯一主类");
        }
        mealCategory.setName(null);
        mealCategory.setDeptId(null);
        mealCategory.setUpdateBy(SecurityUtils.getUsername());
        return mealCategoryMapper.updateMealCategory(mealCategory);
    }

    /**
     * 分类删除不开放：分类表固定 9 条主类
     *
     * @param categoryIds 分类ID数组
     * @return 永不返回，始终抛错
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteMealCategoryByIds(Long[] categoryIds) {
        throw new ServiceException("分类固定为 9 个唯一主类，不支持删除");
    }
}
