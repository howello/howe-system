package com.howe.meal.service.impl;

import com.howe.common.exception.ServiceException;
import com.howe.common.utils.SecurityUtils;
import com.howe.meal.constant.MealCategoryTaxonomy;
import com.howe.meal.domain.MealCategory;
import com.howe.meal.domain.dto.MealCategoryReviewResult;
import com.howe.meal.domain.dto.MealCategorySaveRequest;
import com.howe.meal.domain.dto.MealCategorySaveResult;
import com.howe.meal.mapper.MealCategoryMapper;
import com.howe.meal.mapper.MealCategoryRefMapper;
import com.howe.meal.service.IMealCategoryReviewService;
import com.howe.meal.service.IMealCategoryService;
import com.howe.meal.util.MealCategoryContentGuard;
import com.howe.meal.util.MealCategoryIconGuard;
import com.howe.meal.util.MealScopeGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 点餐-菜品分类 服务层实现
 *
 * <p>公共分类由 {@link MealCategoryTaxonomy} 锁定名称与数量；家庭私有分类走同一套
 * AI 审阅流程。AI 在事务外调用，落库在短事务内完成，并在写入前做二次重名与归属校验。</p>
 *
 * @author howe
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MealCategoryServiceImpl implements IMealCategoryService {

    /** 操作类型：新增 */
    private static final String OP_CREATE = "CREATE";
    /** 操作类型：修改 */
    private static final String OP_UPDATE = "UPDATE";

    private final MealCategoryMapper mealCategoryMapper;

    private final MealCategoryRefMapper mealCategoryRefMapper;

    private final IMealCategoryReviewService mealCategoryReviewService;

    @Override
    public List<MealCategory> selectMealCategoryList(MealCategory mealCategory) {
        applyListScope(mealCategory);
        return mealCategoryMapper.selectMealCategoryList(mealCategory);
    }

    /**
     * 分类详情同样是「按 id 取详情」，不受数据权限保护：
     * 公共分类谁都能看，家庭私有分类只有本家庭与平台管理员能看。
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
     * 新增家庭私有分类
     *
     * <p>公共分类不允许新增；平台管理员可指定任意有效家庭，家庭账号强制使用登录家庭。</p>
     */
    @Override
    public MealCategorySaveResult insertMealCategory(MealCategorySaveRequest request) {
        Long deptId = resolveCreateDeptId(request.getDeptId());
        MealCategoryReviewResult review = mealCategoryReviewService.review(request, OP_CREATE, null, deptId);
        if (review != null && !Boolean.TRUE.equals(review.getApproved())) {
            return rejectedResult(review);
        }
        MealCategorySaveRequest effective = review == null ? normalize(request) : normalize(review, request);
        checkContent(effective);
        checkNameAvailable(effective.getName(), deptId, null);
        MealCategory entity = toEntity(effective, deptId);
        entity.setCreateBy(SecurityUtils.getUsername());
        mealCategoryMapper.insertMealCategory(entity);
        MealCategorySaveResult result = savedResult(entity, request, review);
        result.setCategory(mealCategoryMapper.selectMealCategoryById(entity.getCategoryId()));
        return result;
    }

    /**
     * 修改分类
     *
     * <p>公共分类只允许平台管理员修改非名称字段；私有分类允许所属家庭管理员与超管改名。
     * 归属不允许通过修改接口迁移。</p>
     */
    @Override
    public MealCategorySaveResult updateMealCategory(Long categoryId, MealCategorySaveRequest request) {
        MealCategory exist = requireCategory(categoryId);
        boolean isPublic = isPublic(exist);
        if (isPublic) {
            if (!SecurityUtils.isAdmin()) {
                throw new ServiceException("公共分类只能由平台管理员维护");
            }
        } else {
            MealScopeGuard.assertSameDept(exist.getDeptId(), "分类");
        }
        Long ownerDeptId = exist.getDeptId() == null ? 0L : exist.getDeptId();
        MealCategoryReviewResult review = mealCategoryReviewService.review(request, OP_UPDATE, categoryId, ownerDeptId);
        if (review != null && !Boolean.TRUE.equals(review.getApproved())) {
            return rejectedResult(review);
        }
        MealCategorySaveRequest effective = review == null ? normalize(request) : normalize(review, request);
        if (isPublic && !Objects.equals(effective.getName().trim(), exist.getName())) {
            throw new ServiceException("公共分类名称固定，不支持修改");
        }
        checkContent(effective);
        checkNameAvailable(effective.getName(), ownerDeptId, categoryId);
        MealCategory update = toEntity(effective, ownerDeptId);
        update.setCategoryId(categoryId);
        update.setUpdateBy(SecurityUtils.getUsername());
        mealCategoryMapper.updateMealCategory(update);
        MealCategorySaveResult result = savedResult(update, request, review);
        result.setCategory(mealCategoryMapper.selectMealCategoryById(categoryId));
        return result;
    }

    /**
     * 批量删除家庭私有分类
     *
     * <p>整批先校验再写入，任一条不合法则整批失败，避免部分成功。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteMealCategoryByIds(Long[] categoryIds) {
        if (categoryIds == null || categoryIds.length == 0) {
            throw new ServiceException("请选择要删除的分类");
        }
        for (Long categoryId : categoryIds) {
            MealCategory exist = requireCategory(categoryId);
            if (isPublic(exist)) {
                throw new ServiceException("公共分类不允许删除");
            }
            MealScopeGuard.assertSameDept(exist.getDeptId(), "分类");
            int refs = mealCategoryRefMapper.countByCategoryId(categoryId);
            if (refs > 0) {
                throw new ServiceException("分类「" + exist.getName() + "」下还有 " + refs + " 项菜品或提案，请先移动后再删除");
            }
        }
        return mealCategoryMapper.deleteMealCategoryByIds(categoryIds);
    }

    /**
     * 列表范围：家庭账号强制只看「公共 + 本家庭」，超管可指定家庭或看全部
     *
     * @param query 查询条件
     */
    private void applyListScope(MealCategory query) {
        if (SecurityUtils.isAdmin()) {
            // 超管：null 表示全部；0 表示只看公共分类；>0 表示「公共 + 该家庭」
            return;
        }
        Long deptId = SecurityUtils.getDeptId();
        if (deptId == null || deptId <= 0) {
            throw new ServiceException("当前账号没有可用的家庭归属");
        }
        query.setDeptId(deptId);
    }

    /**
     * 解析新增分类的归属：公共分类不可新增，家庭账号强制本家庭
     *
     * @param requested 前端传入的归属
     * @return 归属家庭 ID
     */
    private Long resolveCreateDeptId(Long requested) {
        if (SecurityUtils.isAdmin()) {
            if (requested == null) {
                throw new ServiceException("请选择分类归属的家庭");
            }
            if (requested <= 0) {
                throw new ServiceException("公共分类不允许新增，请选择有效家庭");
            }
            return requested;
        }
        Long deptId = SecurityUtils.getDeptId();
        if (deptId == null || deptId <= 0) {
            throw new ServiceException("当前账号没有可用的家庭归属");
        }
        if (requested != null && !requested.equals(deptId)) {
            throw new ServiceException("只能新增本家庭的分类");
        }
        return deptId;
    }

    /**
     * 校验最终落库的文本内容
     *
     * <p>固定提示词里的「纯文本 + 长度上限」不能只靠模型遵守：AI 润色结果在落库前
     * 必须再走一次本地校验，避免 HTML、Markdown 链接、URL 或超长文本被写入。</p>
     *
     * @param request 已合并 AI 润色值（或降级时为用户原值）的请求
     */
    private void checkContent(MealCategorySaveRequest request) {
        String violation = MealCategoryContentGuard.violation(
                request.getName(), request.getDescription(), request.getBasis());
        if (violation != null) {
            throw new ServiceException("分类内容不符合要求：" + violation);
        }
    }

    /**
     * 重名校验：同一归属内不允许重名，私有分类也不允许与公共分类同名
     *
     * @param name       分类名称
     * @param ownerDeptId 归属家庭 ID
     * @param excludeId  修改时排除自身
     */
    private void checkNameAvailable(String name, Long ownerDeptId, Long excludeId) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            throw new ServiceException("分类名称不能为空");
        }
        if (MealCategoryTaxonomy.isSignature(trimmed)) {
            throw new ServiceException("「招牌」不是分类，不能写入分类表");
        }
        MealCategory query = new MealCategory();
        query.setDeptId(ownerDeptId);
        for (MealCategory category : mealCategoryMapper.selectMealCategoryList(query)) {
            if (excludeId != null && excludeId.equals(category.getCategoryId())) {
                continue;
            }
            if (trimmed.equalsIgnoreCase(Objects.toString(category.getName(), "").trim())) {
                throw new ServiceException("已存在同名分类「" + trimmed + "」");
            }
        }
        // 私有分类不得与 8 个公共分类同名
        if (ownerDeptId != null && ownerDeptId.longValue() != 0L
                && MealCategoryTaxonomy.isPrimaryCategory(trimmed)) {
            throw new ServiceException("「" + trimmed + "」是公共分类名称，家庭分类不能重名");
        }
    }

    /**
     * 规范化请求：去空白、补默认值、校验图标
     *
     * @param request 原始请求
     * @return 规范化后的请求
     */
    private MealCategorySaveRequest normalize(MealCategorySaveRequest request) {
        MealCategorySaveRequest normalized = new MealCategorySaveRequest();
        normalized.setDeptId(request.getDeptId());
        normalized.setName(trim(request.getName()));
        normalized.setDescription(trim(request.getDescription()));
        normalized.setBasis(trim(request.getBasis()));
        normalized.setIcon(MealCategoryIconGuard.validateIcon(request.getIcon()));
        normalized.setSort(request.getSort() == null ? 0 : request.getSort());
        normalized.setStatus(isBlank(request.getStatus()) ? "0" : request.getStatus());
        normalized.setRemark(trim(request.getRemark()));
        return normalized;
    }

    /**
     * 用 AI 润色值覆盖用户输入（图标仍取用户原值）
     *
     * @param review  AI 结果
     * @param request 用户原始请求
     * @return 规范化后的请求
     */
    private MealCategorySaveRequest normalize(MealCategoryReviewResult review, MealCategorySaveRequest request) {
        MealCategorySaveRequest merged = new MealCategorySaveRequest();
        merged.setDeptId(request.getDeptId());
        merged.setName(trim(review.getName()));
        merged.setDescription(trim(review.getDescription()));
        merged.setBasis(trim(review.getBasis()));
        merged.setIcon(MealCategoryIconGuard.validateIcon(request.getIcon()));
        merged.setSort(request.getSort() == null ? 0 : request.getSort());
        merged.setStatus(isBlank(request.getStatus()) ? "0" : request.getStatus());
        merged.setRemark(trim(request.getRemark()));
        return merged;
    }

    /**
     * 请求转实体
     *
     * @param request 请求
     * @param deptId  归属
     * @return 实体
     */
    private MealCategory toEntity(MealCategorySaveRequest request, Long deptId) {
        MealCategory entity = new MealCategory();
        entity.setDeptId(deptId);
        entity.setName(request.getName());
        entity.setDescription(request.getDescription());
        entity.setBasis(request.getBasis());
        entity.setIcon(request.getIcon());
        entity.setSort(request.getSort());
        entity.setStatus(request.getStatus());
        entity.setRemark(request.getRemark());
        return entity;
    }

    /**
     * 构造拒绝结果（不落库）
     *
     * @param review AI 结果
     * @return 结果
     */
    private MealCategorySaveResult rejectedResult(MealCategoryReviewResult review) {
        MealCategorySaveResult result = new MealCategorySaveResult();
        result.setApproved(false);
        result.setSaved(false);
        result.setReviewStatus(MealCategorySaveResult.REVIEW_REJECTED);
        result.setReason(review.getReason());
        List<String> examples = review.getExamples();
        result.setExamples(examples == null ? new ArrayList<>() : examples);
        return result;
    }

    /**
     * 构造保存成功结果
     *
     * @param saved   实际保存值
     * @param request 用户原始输入
     * @param review  AI 结果；null 表示降级放行
     * @return 结果
     */
    private MealCategorySaveResult savedResult(MealCategory saved, MealCategorySaveRequest request,
            MealCategoryReviewResult review) {
        MealCategorySaveResult result = new MealCategorySaveResult();
        result.setSaved(true);
        if (review == null) {
            result.setApproved(null);
            result.setReviewStatus(MealCategorySaveResult.REVIEW_SKIPPED);
        } else {
            result.setApproved(true);
            result.setReviewStatus(MealCategorySaveResult.REVIEW_APPROVED);
        }
        List<MealCategorySaveResult.FieldChange> changes = new ArrayList<>();
        addChange(changes, "name", request.getName(), saved.getName());
        addChange(changes, "description", request.getDescription(), saved.getDescription());
        addChange(changes, "basis", request.getBasis(), saved.getBasis());
        addChange(changes, "icon", request.getIcon(), saved.getIcon());
        result.setChanges(changes);
        return result;
    }

    /**
     * 记录字段差异
     *
     * @param changes 差异列表
     * @param field   字段名
     * @param before  原值
     * @param after   保存值
     */
    private void addChange(List<MealCategorySaveResult.FieldChange> changes, String field, String before, String after) {
        String beforeValue = trim(before);
        String afterValue = trim(after);
        if (!Objects.equals(beforeValue, afterValue)) {
            changes.add(new MealCategorySaveResult.FieldChange(field, beforeValue, afterValue));
        }
    }

    /**
     * 读取分类，不存在抛错
     *
     * @param categoryId 分类 ID
     * @return 分类
     */
    private MealCategory requireCategory(Long categoryId) {
        MealCategory category = categoryId == null ? null : mealCategoryMapper.selectMealCategoryById(categoryId);
        if (category == null) {
            throw new ServiceException("分类不存在或已删除");
        }
        return category;
    }

    /**
     * 判断是否公共分类
     *
     * @param category 分类
     * @return 公共分类返回 true
     */
    private boolean isPublic(MealCategory category) {
        return category.getDeptId() != null
                && category.getDeptId().longValue() == MealCategoryTaxonomy.PUBLIC_DEPT_ID;
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
