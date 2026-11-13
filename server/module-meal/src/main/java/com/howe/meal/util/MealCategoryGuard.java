package com.howe.meal.util;

import com.howe.common.exception.ServiceException;
import com.howe.meal.constant.MealCategoryTaxonomy;
import com.howe.meal.domain.MealCategory;
import com.howe.meal.mapper.MealCategoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 分类归属校验器
 *
 * <p>菜品、提案、AI 审核与 AI 候选装载共用同一套规则，避免各处重复写
 * 「必须是 9 个公共主类」之类的硬编码判断。</p>
 *
 * <p>规则：</p>
 * <ul>
 *   <li>最多绑定 1 个分类（唯一主类）；</li>
 *   <li>分类必须存在、未删除、启用；</li>
 *   <li>公共分类（{@code dept_id = 0}）必须是 {@link MealCategoryTaxonomy} 定义的 9 个名称之一；</li>
 *   <li>公共菜（{@code ownerDeptId = 0}）只能绑定公共分类；</li>
 *   <li>家庭私有菜与提案可绑定公共分类或<b>本家庭</b>的私有分类。</li>
 * </ul>
 *
 * @author howe
 */
@Component
@RequiredArgsConstructor
public class MealCategoryGuard {

    private final MealCategoryMapper mealCategoryMapper;

    /**
     * 解析并校验唯一分类 ID（写入场景，失败抛错）
     *
     * @param categoryIds 入参分类列表
     * @param ownerDeptId 业务归属家庭 ID（菜品/提案的 dept_id）
     * @param bizLabel    业务名称，用于拼错误提示（如「菜品」「提案」）
     * @return 唯一分类 ID；未选择返回 null
     * @throws ServiceException 分类数量、归属或状态不合法
     */
    public Long resolveWritableCategoryId(List<Long> categoryIds, Long ownerDeptId, String bizLabel) {
        if (categoryIds == null || categoryIds.isEmpty()) {
            return null;
        }
        if (categoryIds.size() > 1) {
            throw new ServiceException("一" + unitOf(bizLabel) + bizLabel + "只能归一个主类");
        }
        Long categoryId = categoryIds.get(0);
        if (categoryId == null) {
            return null;
        }
        MealCategory category = mealCategoryMapper.selectMealCategoryById(categoryId);
        if (category == null) {
            throw new ServiceException("分类不存在或已删除");
        }
        if (!"0".equals(category.getStatus())) {
            throw new ServiceException("分类「" + category.getName() + "」已停用，不能选择");
        }
        boolean isPublic = category.getDeptId() != null
                && category.getDeptId().longValue() == MealCategoryTaxonomy.PUBLIC_DEPT_ID;
        if (isPublic) {
            // 公共分类不要求名称属于 9 个固定名称：私有分类升级为公共后可以保留自定义名称
            return categoryId;
        }
        // 私有分类：必须是当前业务归属的那个家庭
        if (ownerDeptId == null || ownerDeptId.longValue() == 0L) {
            throw new ServiceException("公共" + bizLabel + "只能选择公共分类");
        }
        if (!ownerDeptId.equals(category.getDeptId())) {
            throw new ServiceException("只能选择公共分类或本家庭的分类");
        }
        return categoryId;
    }

    /**
     * 解析并校验唯一分类 ID（异步/只读场景，非法值静默返回 null）
     *
     * <p>用于 AI 审核这类「拿不到就当作未分类」的场景：不抛错，避免一条
     * 非法分类让整次审核失败。</p>
     *
     * @param categoryId  待校验的分类 ID
     * @param ownerDeptId 业务归属家庭 ID
     * @return 合法分类 ID；非法或未选择返回 null
     */
    public Long resolveCategoryIdOrNull(Long categoryId, Long ownerDeptId) {
        if (categoryId == null) {
            return null;
        }
        MealCategory category = mealCategoryMapper.selectMealCategoryById(categoryId);
        if (category == null || !"0".equals(category.getStatus())) {
            return null;
        }
        boolean isPublic = category.getDeptId() != null
                && category.getDeptId().longValue() == MealCategoryTaxonomy.PUBLIC_DEPT_ID;
        if (isPublic) {
            return categoryId;
        }
        if (ownerDeptId == null || ownerDeptId.longValue() == 0L) {
            return null;
        }
        return ownerDeptId.equals(category.getDeptId()) ? categoryId : null;
    }

    /**
     * 判断分类是否可写入指定归属（供 AI 候选装载使用）
     *
     * @param category    分类
     * @param ownerDeptId 业务归属家庭 ID
     * @return 可写入返回 true
     */
    public boolean isSelectable(MealCategory category, Long ownerDeptId) {
        if (category == null || !"0".equals(category.getStatus())) {
            return false;
        }
        boolean isPublic = category.getDeptId() != null
                && category.getDeptId().longValue() == MealCategoryTaxonomy.PUBLIC_DEPT_ID;
        if (isPublic) {
            return true;
        }
        return ownerDeptId != null && ownerDeptId.longValue() != 0L
                && ownerDeptId.equals(category.getDeptId());
    }

    /**
     * 量词：菜品用「道」，提案用「条」
     *
     * @param bizLabel 业务名称
     * @return 量词
     */
    private String unitOf(String bizLabel) {
        return isProposal(bizLabel) ? "条" : "道";
    }

    private boolean isProposal(String bizLabel) {
        return "提案".equals(bizLabel);
    }
}
