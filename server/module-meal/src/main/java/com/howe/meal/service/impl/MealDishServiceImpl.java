package com.howe.meal.service.impl;

import com.howe.common.exception.ServiceException;
import com.howe.common.utils.SecurityUtils;
import com.howe.meal.constant.MealCategoryTaxonomy;
import com.howe.meal.domain.MealCategory;
import com.howe.meal.domain.MealCategoryRef;
import com.howe.meal.domain.MealDish;
import com.howe.meal.mapper.MealCategoryMapper;
import com.howe.meal.mapper.MealCategoryRefMapper;
import com.howe.meal.mapper.MealDishMapper;
import com.howe.meal.service.IMealDishService;
import com.howe.meal.util.MealScopeGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 点餐-菜品 服务层实现
 *
 * <p>分类是唯一主类：菜品最多绑定 1 个分类，写入前统一走 {@link #resolveSingleCategoryId} 校验；
 * 未选主类时允许保存，菜品以「待分类」状态出现在管理端。</p>
 *
 * @author howe
 */
@Service
@RequiredArgsConstructor
public class MealDishServiceImpl implements IMealDishService {

    /** 招牌统计窗口天数 */
    private static final int SIGNATURE_DAYS = 90;

    /** 招牌返回条数 */
    private static final int SIGNATURE_TOP_N = 3;

    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    private final MealDishMapper mealDishMapper;
    private final MealCategoryRefMapper mealCategoryRefMapper;
    private final MealCategoryMapper mealCategoryMapper;

    @Override
    public List<MealDish> selectMealDishList(MealDish mealDish) {
        // 平台管理员不套用部门过滤：未显式指定部门时可看到所有家庭的菜品（含 AI 审核上架的菜）；
        // 家庭账号仍只看「本家庭 + 公共」。
        if (mealDish.getDeptId() == null && !SecurityUtils.isAdmin()) {
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
        if (!isPublic) {
            MealScopeGuard.assertSameDept(dish.getDeptId(), "菜品");
        }
        // 回填关联分类，供管理端编辑表单回显
        dish.setCategoryIds(singleCategoryList(dishId));
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
        Long categoryId = resolveSingleCategoryId(mealDish.getCategoryIds());
        mealDish.setCreateBy(SecurityUtils.getUsername());
        int rows = mealDishMapper.insertMealDish(mealDish);
        saveDishCategory(mealDish.getDishId(), categoryId);
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateMealDish(MealDish mealDish) {
        MealDish exist = mealDishMapper.selectMealDishById(mealDish.getDishId());
        if (exist == null) {
            throw new ServiceException("菜品不存在或已删除");
        }
        MealScopeGuard.assertDishWritable(exist.getDeptId(), "菜品");
        // 归属不允许通过修改接口迁移
        mealDish.setDeptId(null);
        Long categoryId = resolveSingleCategoryId(mealDish.getCategoryIds());
        mealDish.setUpdateBy(SecurityUtils.getUsername());
        int rows = mealDishMapper.updateMealDish(mealDish);
        // categoryIds 非空才视为「本次要改分类」；为 null 表示不动分类
        if (mealDish.getCategoryIds() != null) {
            mealCategoryRefMapper.deleteMealCategoryRefs(MealCategoryRef.BIZ_DISH,
                    new Long[] { mealDish.getDishId() });
            saveDishCategory(mealDish.getDishId(), categoryId);
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
            // 删除公共菜会影响所有家庭，仍保持「公共数据只能由平台管理员维护」
            MealScopeGuard.assertWritable(exist.getDeptId(), "菜品");
        }
        // 菜品逻辑删除，关联表按业务物理清理，避免残留引用
        mealCategoryRefMapper.deleteMealCategoryRefs(MealCategoryRef.BIZ_DISH, dishIds);
        return mealDishMapper.deleteMealDishByIds(dishIds);
    }

    /**
     * 招牌推荐位：本家庭近 90 天已完成订单中按份数取前 3 道仍上架的菜
     *
     * <p>只读聚合，不落库；已下架或已删除的候选被跳过并由后续菜品顺延补位。
     * 平台管理员无家庭时按全量统计。</p>
     *
     * @return 按份数倒序的招牌菜品，最多 3 道
     */
    @Override
    public List<MealDish> selectSignatureDishes() {
        Long deptId = SecurityUtils.isAdmin() ? null : SecurityUtils.getDeptId();
        LocalDate today = LocalDate.now();
        String startDay = today.minusDays(SIGNATURE_DAYS - 1L).format(DAY_FMT);
        String endDay = today.format(DAY_FMT);
        List<MealDish> dishes = mealDishMapper.selectSignatureDishes(deptId, startDay, endDay, SIGNATURE_TOP_N);
        return dishes == null ? new ArrayList<>() : dishes;
    }

    /**
     * 校验并解析唯一主类 ID
     *
     * <p>null 或空列表表示不绑定主类；多于一个直接拒绝；分类必须存在、未删除且为平台公共主类。</p>
     *
     * @param categoryIds 入参分类列表
     * @return 唯一分类 ID；未选择返回 null
     */
    private Long resolveSingleCategoryId(List<Long> categoryIds) {
        if (categoryIds == null || categoryIds.isEmpty()) {
            return null;
        }
        if (categoryIds.size() > 1) {
            throw new ServiceException("一道菜只能归一个主类");
        }
        Long categoryId = categoryIds.get(0);
        if (categoryId == null) {
            return null;
        }
        MealCategory category = mealCategoryMapper.selectMealCategoryById(categoryId);
        if (category == null) {
            throw new ServiceException("分类不存在或已删除");
        }
        if (category.getDeptId() == null || category.getDeptId() != MealCategoryTaxonomy.PUBLIC_DEPT_ID) {
            throw new ServiceException("只能选择平台公共主类");
        }
        if (!MealCategoryTaxonomy.isPrimaryCategory(category.getName())) {
            throw new ServiceException("只能选择 9 个唯一主类之一");
        }
        return categoryId;
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
        Map<Long, Long> grouped = new HashMap<>();
        for (MealCategoryRef ref : mealCategoryRefMapper.selectRefsByBizIds(MealCategoryRef.BIZ_DISH, dishIds)) {
            grouped.putIfAbsent(ref.getBizId(), ref.getCategoryId());
        }
        for (MealDish dish : list) {
            Long categoryId = grouped.get(dish.getDishId());
            List<Long> ids = new ArrayList<>(1);
            if (categoryId != null) {
                ids.add(categoryId);
            }
            dish.setCategoryIds(ids);
        }
    }

    /** 查询某道菜的唯一分类 ID 并包成列表（详情回显用） */
    private List<Long> singleCategoryList(Long dishId) {
        List<Long> ids = new ArrayList<>(1);
        Long categoryId = mealCategoryRefMapper.selectCategoryId(MealCategoryRef.BIZ_DISH, dishId);
        if (categoryId != null) {
            ids.add(categoryId);
        }
        return ids;
    }

    /** 写入菜品的唯一分类关联；categoryId 为空则不做任何事 */
    private void saveDishCategory(Long dishId, Long categoryId) {
        if (dishId == null || categoryId == null) {
            return;
        }
        MealCategoryRef ref = new MealCategoryRef();
        ref.setBizType(MealCategoryRef.BIZ_DISH);
        ref.setBizId(dishId);
        ref.setCategoryId(categoryId);
        mealCategoryRefMapper.insertMealCategoryRef(ref);
    }
}
