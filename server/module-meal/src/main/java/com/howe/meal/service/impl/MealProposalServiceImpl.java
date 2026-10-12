package com.howe.meal.service.impl;

import com.howe.common.annotation.DataScope;
import com.howe.common.core.domain.model.LoginUser;
import com.howe.common.exception.ServiceException;
import com.howe.common.utils.SecurityUtils;
import com.howe.common.utils.StringUtils;
import com.howe.meal.domain.MealCategoryRef;
import com.howe.meal.domain.MealProposal;
import com.howe.meal.domain.dto.MealProposalAuditBody;
import com.howe.meal.mapper.MealCategoryRefMapper;
import com.howe.meal.mapper.MealProposalMapper;
import com.howe.meal.service.IMealProposalService;
import com.howe.meal.service.ITransactionCommitExecutor;
import com.howe.meal.service.MealProposalAuditService;
import com.howe.meal.util.MealScopeGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 点餐-新菜提案 服务层实现
 *
 * <p>提交提案后由 {@link MealProposalAuditService} 在事务提交后异步做 AI 自动审核；
 * 人工审核支持对已通过/已驳回的提案改判（复用其 {@code dish_id}，不重复建菜品）。</p>
 *
 * @author howe
 */
@Service
@RequiredArgsConstructor
public class MealProposalServiceImpl implements IMealProposalService {

    private final MealProposalMapper mealProposalMapper;
    private final MealCategoryRefMapper mealCategoryRefMapper;
    private final MealProposalAuditService mealProposalAuditService;
    private final ITransactionCommitExecutor transactionCommitExecutor;

    @Override
    @DataScope(deptAlias = "d")
    public List<MealProposal> selectMealProposalList(MealProposal mealProposal) {
        return mealProposalMapper.selectMealProposalList(mealProposal);
    }

    @Override
    public List<MealProposal> selectMyProposalList(MealProposal mealProposal) {
        mealProposal.setUserId(SecurityUtils.getUserId());
        return mealProposalMapper.selectMyProposalList(mealProposal);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int submitProposal(MealProposal mealProposal) {
        mealProposal.setProposalId(null);
        mealProposal.setDeptId(SecurityUtils.getDeptId());
        mealProposal.setUserId(SecurityUtils.getUserId());
        mealProposal.setUserName(currentNickName());
        mealProposal.setStatus(MealProposal.STATUS_PENDING);
        mealProposal.setDishId(null);
        mealProposal.setCreateBy(SecurityUtils.getUsername());
        int rows = mealProposalMapper.insertMealProposal(mealProposal);
        saveProposalCategories(mealProposal.getProposalId(), mealProposal.getCategoryIds());
        // 事务提交后异步触发 AI 自动审核，提交请求不等待 AI
        Long proposalId = mealProposal.getProposalId();
        transactionCommitExecutor.afterCommit(() -> mealProposalAuditService.submitAiAudit(proposalId));
        return rows;
    }

    /**
     * 人工审核：允许对任意状态（待审核/已通过/已驳回）改判。
     * 通过时若提案尚无菜品则用现有字段新建，已有菜品则重新上架；驳回时把已有菜品下架，均不重复创建菜品。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int auditProposal(MealProposalAuditBody body) {
        boolean approved = MealProposal.STATUS_APPROVED.equals(body.getStatus());
        boolean rejected = MealProposal.STATUS_REJECTED.equals(body.getStatus());
        if (!approved && !rejected) {
            throw new ServiceException("审核结果只能是 1（通过）或 2（驳回）");
        }

        MealProposal proposal = mealProposalMapper.selectMealProposalById(body.getProposalId());
        if (proposal == null) {
            throw new ServiceException("提案不存在或已删除");
        }
        MealScopeGuard.assertSameDept(proposal.getDeptId(), "提案");

        MealProposal update = new MealProposal();
        update.setProposalId(proposal.getProposalId());
        update.setStatus(body.getStatus());
        // 期望状态：与 AI 自动审核并发时只有一个能改动成功
        update.setExpectedStatus(proposal.getStatus());
        update.setAuditBy(currentNickName());
        update.setAuditRemark(body.getAuditRemark());
        update.setUpdateBy(SecurityUtils.getUsername());

        if (approved) {
            if (proposal.getDishId() == null) {
                // 尚未生成菜品：用提案现有字段新建并回写菜品ID
                List<Long> categoryIds = mealCategoryRefMapper.selectCategoryIds(MealCategoryRef.BIZ_PROPOSAL,
                        proposal.getProposalId());
                update.setDishId(
                        mealProposalAuditService.createDishFromProposal(proposal, categoryIds, currentNickName()));
            } else {
                // 已生成过菜品：重新上架，不重复创建
                mealProposalAuditService.setDishStatus(proposal.getDishId(), "0");
            }
        } else if (proposal.getDishId() != null) {
            // 驳回已上架的提案：把对应菜品下架
            mealProposalAuditService.setDishStatus(proposal.getDishId(), "1");
        }

        int rows = mealProposalMapper.updateMealProposal(update);
        if (rows == 0) {
            throw new ServiceException("提案状态已变化，请刷新后重试");
        }
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteMealProposalByIds(Long[] proposalIds) {
        if (proposalIds == null || proposalIds.length == 0) {
            throw new ServiceException("请选择要删除的数据");
        }
        for (Long proposalId : proposalIds) {
            MealProposal exist = mealProposalMapper.selectMealProposalById(proposalId);
            if (exist == null) {
                throw new ServiceException("提案不存在或已删除");
            }
            MealScopeGuard.assertSameDept(exist.getDeptId(), "提案");
        }
        // 提案逻辑删除，关联表按业务物理清理
        mealCategoryRefMapper.deleteMealCategoryRefs(MealCategoryRef.BIZ_PROPOSAL, proposalIds);
        return mealProposalMapper.deleteMealProposalByIds(proposalIds);
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

    private String currentNickName() {
        LoginUser loginUser = SecurityUtils.getLoginUser();
        if (loginUser == null) {
            return SecurityUtils.getUsername();
        }
        String nickName = loginUser.getUser() != null ? loginUser.getUser().getNickName() : null;
        return StringUtils.isNotEmpty(nickName) ? nickName : loginUser.getUsername();
    }
}
