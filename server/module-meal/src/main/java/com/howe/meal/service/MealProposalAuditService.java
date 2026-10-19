package com.howe.meal.service;

import com.howe.common.exception.ServiceException;
import com.howe.common.utils.StringUtils;
import com.howe.common.utils.spring.SpringUtils;
import com.howe.meal.domain.MealCategoryRef;
import com.howe.meal.domain.MealDish;
import com.howe.meal.domain.MealProposal;
import com.howe.meal.domain.dto.DishAiAuditResult;
import com.howe.meal.domain.dto.DishAiCoverResult;
import com.howe.meal.domain.dto.DishAiGenerateRequest;
import com.howe.meal.domain.dto.DishAiTextResult;
import com.howe.meal.mapper.MealCategoryRefMapper;
import com.howe.meal.mapper.MealDishMapper;
import com.howe.meal.mapper.MealProposalMapper;
import com.howe.meal.service.IMealWeComNotifyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * 新菜提案 AI 自动审核服务
 *
 * <p>提案提交事务提交后，在线程池里对提案做一次 AI 审核：一次结构化调用同时完成「判定是否可制作」
 * 与「补齐缺失字段」。通过 → 补齐字段 + 生成封面 + 在 {@code meal_dish} 落一条 {@code source=1} 的菜品
 * 并回写 {@code dish_id}；驳回 → 置状态并写可读原因；AI 调用失败 → 保留待审核并写可读原因，等待人工兜底。</p>
 *
 * <p>异步线程没有登录态，家庭归属取自提案的 {@code deptId}，不读 {@code SecurityUtils}。
 * 审核通过/驳回只从「待审核」出发，状态更新带期望状态条件，避免与管理员人工审核并发覆盖。</p>
 *
 * @author howe
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MealProposalAuditService {

    /** 业务线程池的 bean 名，由 module-framework 提供 */
    private static final String EXECUTOR_BEAN = "threadPoolTaskExecutor";

    /** AI 自动审核写入的审核人标识 */
    private static final String AI_AUDITOR = "AI 自动审核";

    /** AI 审核通过时写入的审核意见 */
    private static final String AI_APPROVE_REMARK = "AI 自动审核通过，已自动上架到菜品列表";

    /** AI 驳回但未给出原因时的兜底文案 */
    private static final String AI_REJECT_FALLBACK = "AI 判断该提案暂不适合上架";

    private final MealProposalMapper mealProposalMapper;

    private final MealDishMapper mealDishMapper;

    private final MealCategoryRefMapper mealCategoryRefMapper;

    private final MealDishAiService mealDishAiService;

    private final IMealWeComNotifyService mealWeComNotifyService;

    private final TransactionTemplate transactionTemplate;

    /** 线程池，延迟解析：容器未就绪或模块单独使用时退化为同步执行 */
    private volatile Executor executor;

    /**
     * 提交一次异步 AI 自动审核；线程池不可用时同步执行，保证功能可用
     *
     * @param proposalId 提案 ID
     */
    public void submitAiAudit(Long proposalId) {
        if (proposalId == null) {
            return;
        }
        Executor target = resolveExecutor();
        if (target == null) {
            auditByAi(proposalId);
            return;
        }
        try {
            target.execute(() -> auditByAi(proposalId));
        } catch (Exception e) {
            log.warn("提交新菜提案 AI 自动审核任务失败，proposalId={}", proposalId, e);
        }
    }

    /**
     * 执行 AI 自动审核（不依赖登录态）：加载提案 → 调模型 → 事务内落库
     *
     * @param proposalId 提案 ID
     */
    public void auditByAi(Long proposalId) {
        MealProposal proposal = mealProposalMapper.selectMealProposalById(proposalId);
        if (proposal == null || !MealProposal.STATUS_PENDING.equals(proposal.getStatus())) {
            return;
        }
        DishAiAuditResult audit;
        try {
            audit = mealDishAiService.auditAndComplete(proposal.getName(), snapshotCurrent(proposal),
                    proposal.getDeptId());
        } catch (Exception e) {
            log.warn("新菜提案 AI 自动审核调用失败，proposalId={}", proposalId, e);
            markAuditFailed(proposalId, e);
            notifyAuditResult(proposal, "审核未完成", readableMessage(e), null);
            return;
        }
        try {
            if (!audit.isApproved()) {
                applyReject(proposalId, audit.getRejectReason());
                notifyAuditResult(proposal, "已驳回", readableAuditReason(audit.getRejectReason()), null);
            } else {
                applyApprove(proposal, audit);
                notifyAuditResult(proposal, "已通过", "已经自动补齐资料并加进菜单", null);
            }
        } catch (Exception e) {
            // 写入失败通常是提案状态已被人工审核抢先改动，事务已回滚，不影响人工结果
            log.warn("写入新菜提案 AI 审核结果失败，proposalId={}", proposalId, e);
            notifyAuditResult(proposal, "审核未完成", "结果没能保存成功，等家人手动看一下", null);
        }
    }

    /**
     * 发送提案 AI 审核结果通知
     *
     * @param proposal 提案
     * @param result   结果
     * @param reason   可读原因
     * @param model    模型名，可为空
     */
    private void notifyAuditResult(MealProposal proposal, String result, String reason, String model) {
        try {
            mealWeComNotifyService.notifyProposalAiAudit(proposal.getDeptId(), proposal.getProposalId(),
                    proposal.getName(), proposal.getUserId(), result, reason, model);
        } catch (Exception e) {
            log.warn("发送提案 AI 审核企业微信通知失败，proposalId={}", proposal.getProposalId(), e);
        }
    }

    /**
     * 取异常的可读提示，避免把堆栈写进通知
     *
     * @param e 异常
     * @return 可读提示
     */
    private String readableMessage(Exception e) {
        return e instanceof ServiceException && e.getMessage() != null ? e.getMessage() : "AI 这次没有帮上忙";
    }

    /**
     * 取审核驳回原因，缺省时用兜底文案
     *
     * @param rejectReason 驳回原因
     * @return 可读原因
     */
    private String readableAuditReason(String rejectReason) {
        return StringUtils.isNotEmpty(rejectReason) ? rejectReason : AI_REJECT_FALLBACK;
    }

    /**
     * 用提案现有字段创建菜品（人工审核通过时使用），返回菜品 ID
     *
     * @param proposal    提案
     * @param categoryIds 菜品分类 ID 列表
     * @param operator    操作人
     * @return 新菜品 ID
     */
    public Long createDishFromProposal(MealProposal proposal, List<Long> categoryIds, String operator) {
        return insertDish(proposal.getDeptId(), proposal.getName(), proposal.getImage(), proposal.getDescription(),
                proposal.getTags(), proposal.getDuration(), proposal.getLevel(), null, null,
                proposal.getIngredients(), proposal.getSteps(), proposal.getTips(), categoryIds, operator);
    }

    /**
     * 上下架菜品：{@code 0} 上架、{@code 1} 下架
     *
     * @param dishId 菜品 ID
     * @param status 目标状态
     */
    public void setDishStatus(Long dishId, String status) {
        if (dishId == null) {
            return;
        }
        MealDish update = new MealDish();
        update.setDishId(dishId);
        update.setStatus(status);
        mealDishMapper.updateMealDish(update);
    }

    /** AI 审核通过：补齐字段 + 生成封面 + 建菜品 + 回写提案（同一事务） */
    private void applyApprove(MealProposal proposal, DishAiAuditResult audit) {
        DishAiTextResult text = audit.getText();
        Map<String, Object> fields = text.getFields();
        if (fields == null) {
            fields = Map.of();
        }
        // 分类：优先 AI 匹配结果；提案原本已选分类或 AI 未匹配时沿用提案原分类
        List<Long> existingCategoryIds = mealCategoryRefMapper.selectCategoryIds(MealCategoryRef.BIZ_PROPOSAL,
                proposal.getProposalId());
        List<Long> matchedCategoryIds = text.getMatchedCategoryIds();
        List<Long> categoryIds = matchedCategoryIds != null && !matchedCategoryIds.isEmpty()
                ? matchedCategoryIds : existingCategoryIds;
        // 提案原本没有分类时，把 AI 匹配到的分类一并写入提案，便于人工查看与后续改判
        boolean fillProposalCategories = (existingCategoryIds == null || existingCategoryIds.isEmpty())
                && categoryIds != null && !categoryIds.isEmpty();

        String cover = proposal.getImage();
        if (isBlank(cover)) {
            cover = generateCover(proposal.getName());
        }
        String description = firstNonBlank(proposal.getDescription(), fieldText(fields, "description"));
        String tags = firstNonBlank(proposal.getTags(), fieldText(fields, "tags"));
        String duration = firstNonBlank(proposal.getDuration(), fieldText(fields, "duration"));
        String level = firstNonBlank(proposal.getLevel(), fieldText(fields, "level"));
        String ingredients = firstNonBlank(proposal.getIngredients(), fieldText(fields, "ingredients"));
        String steps = firstNonBlank(proposal.getSteps(), fieldText(fields, "steps"));
        String tips = firstNonBlank(proposal.getTips(), fieldText(fields, "tips"));
        String serve = fieldText(fields, "serve");
        String kcal = fieldText(fields, "kcal");

        String finalCover = cover;
        transactionTemplate.executeWithoutResult(status -> {
            Long dishId = insertDish(proposal.getDeptId(), proposal.getName(), finalCover, description, tags,
                    duration, level, serve, kcal, ingredients, steps, tips, categoryIds, AI_AUDITOR);
            if (fillProposalCategories) {
                saveProposalCategories(proposal.getProposalId(), categoryIds);
            }

            MealProposal update = new MealProposal();
            update.setProposalId(proposal.getProposalId());
            update.setStatus(MealProposal.STATUS_APPROVED);
            update.setExpectedStatus(MealProposal.STATUS_PENDING);
            update.setAuditBy(AI_AUDITOR);
            update.setAuditRemark(AI_APPROVE_REMARK);
            update.setDishId(dishId);
            update.setUpdateBy(AI_AUDITOR);
            // 把补齐后的字段回写提案，便于人工在管理端查看
            update.setDescription(description);
            update.setImage(finalCover);
            update.setTags(tags);
            update.setDuration(duration);
            update.setLevel(level);
            update.setIngredients(ingredients);
            update.setSteps(steps);
            update.setTips(tips);

            int rows = mealProposalMapper.updateMealProposal(update);
            if (rows == 0) {
                throw new ServiceException("提案状态已变化，放弃 AI 自动审核写入");
            }
        });
    }

    /** AI 驳回：置状态并写可读原因 */
    private void applyReject(Long proposalId, String rejectReason) {
        MealProposal update = new MealProposal();
        update.setProposalId(proposalId);
        update.setStatus(MealProposal.STATUS_REJECTED);
        update.setExpectedStatus(MealProposal.STATUS_PENDING);
        update.setAuditBy(AI_AUDITOR);
        update.setAuditRemark(StringUtils.isNotEmpty(rejectReason) ? rejectReason : AI_REJECT_FALLBACK);
        update.setUpdateBy(AI_AUDITOR);
        transactionTemplate.executeWithoutResult(status -> {
            int rows = mealProposalMapper.updateMealProposal(update);
            if (rows == 0) {
                log.info("新菜提案状态已变化，放弃 AI 驳回写入，proposalId={}", proposalId);
            }
        });
    }

    /** AI 调用失败：保留待审核，写入可读原因供人工处理 */
    private void markAuditFailed(Long proposalId, Exception cause) {
        String reason = cause instanceof ServiceException ? cause.getMessage() : "AI 自动审核未完成";
        MealProposal update = new MealProposal();
        update.setProposalId(proposalId);
        update.setExpectedStatus(MealProposal.STATUS_PENDING);
        update.setAuditRemark("AI 自动审核未完成：" + reason + "，等待人工审核");
        update.setUpdateBy(AI_AUDITOR);
        transactionTemplate.executeWithoutResult(status -> mealProposalMapper.updateMealProposal(update));
    }

    /** 写入提案的分类关联；categoryIds 为空则不做任何事 */
    private void saveProposalCategories(Long proposalId, List<Long> categoryIds) {
        if (proposalId == null || categoryIds == null || categoryIds.isEmpty()) {
            return;
        }
        MealCategoryRef ref = new MealCategoryRef();
        ref.setBizType(MealCategoryRef.BIZ_PROPOSAL);
        ref.setBizId(proposalId);
        ref.setCategoryIds(categoryIds);
        mealCategoryRefMapper.insertMealCategoryRefs(ref);
    }

    /** 提案当前已填字段快照（供只补缺失判断） */
    private DishAiGenerateRequest.Current snapshotCurrent(MealProposal proposal) {
        DishAiGenerateRequest.Current current = new DishAiGenerateRequest.Current();
        current.setCategoryIds(
                mealCategoryRefMapper.selectCategoryIds(MealCategoryRef.BIZ_PROPOSAL, proposal.getProposalId()));
        current.setDescription(proposal.getDescription());
        current.setCover(proposal.getImage());
        current.setTags(proposal.getTags());
        current.setDuration(proposal.getDuration());
        current.setLevel(proposal.getLevel());
        current.setIngredients(proposal.getIngredients());
        current.setSteps(proposal.getSteps());
        current.setTips(proposal.getTips());
        return current;
    }

    /** 缺封面时生成封面，失败只记日志并返回 null（不影响上架） */
    private String generateCover(String name) {
        try {
            DishAiGenerateRequest request = new DishAiGenerateRequest();
            request.setName(name);
            DishAiCoverResult coverResult = mealDishAiService.generateCover(request);
            return coverResult.getCover();
        } catch (Exception e) {
            log.warn("新菜提案 AI 封面生成失败，name={}", name, e);
            return null;
        }
    }

    /** 落一条菜品并写入分类关联 */
    private Long insertDish(Long deptId, String name, String cover, String description, String tags, String duration,
            String level, String serve, String kcal, String ingredients, String steps, String tips,
            List<Long> categoryIds, String operator) {
        MealDish dish = new MealDish();
        dish.setDeptId(deptId);
        dish.setName(name);
        dish.setCover(cover);
        dish.setDescription(description);
        dish.setTags(tags);
        dish.setDuration(duration);
        dish.setLevel(level);
        dish.setServe(serve);
        dish.setKcal(kcal);
        dish.setIngredients(ingredients);
        dish.setSteps(steps);
        dish.setTips(tips);
        dish.setStatus("0");
        dish.setSort(0);
        dish.setSource("1");
        dish.setCreateBy(operator);
        mealDishMapper.insertMealDish(dish);
        saveDishCategories(dish.getDishId(), categoryIds);
        return dish.getDishId();
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

    /** 解析业务线程池；容器未就绪或没有该 bean 时返回 null */
    private Executor resolveExecutor() {
        Executor current = this.executor;
        if (current != null) {
            return current;
        }
        try {
            Object bean = SpringUtils.getBean(EXECUTOR_BEAN);
            if (bean instanceof Executor target) {
                this.executor = target;
                return target;
            }
        } catch (Exception e) {
            log.debug("未找到 {} 线程池，AI 自动审核改为同步执行", EXECUTOR_BEAN);
        }
        return null;
    }

    private String firstNonBlank(String preferred, String fallback) {
        return isBlank(preferred) ? fallback : preferred;
    }

    /** 从 AI 补齐结果里取出文本值，缺失返回 null */
    private String fieldText(Map<String, Object> fields, String key) {
        Object value = fields.get(key);
        return value == null ? null : String.valueOf(value).trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
