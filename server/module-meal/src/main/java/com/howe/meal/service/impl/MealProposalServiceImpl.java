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
import com.howe.meal.util.MealCategoryGuard;
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
    private final MealCategoryGuard mealCategoryGuard;
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

    /**
     * 提案详情：家庭内成员都能查看，归属校验在 Service 层完成
     *
     * <p>同一家庭的成员（点餐人 / 厨师 / 管理员）都能打开彼此提交的提案，
     * 但不能跨家庭读取。返回字段包含提交内容、AI 补齐结果与审核信息。</p>
     */
    @Override
    public MealProposal selectMealProposalDetail(Long proposalId) {
        MealProposal proposal = mealProposalMapper.selectMealProposalById(proposalId);
        if (proposal == null) {
            throw new ServiceException("提案不存在或已删除");
        }
        MealScopeGuard.assertSameDept(proposal.getDeptId(), "提案");
        return proposal;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int submitProposal(MealProposal mealProposal) {
        int rows = createProposal(mealProposal);
        // 事务提交后异步触发 AI 自动审核，提交请求不等待 AI
        Long proposalId = mealProposal.getProposalId();
        transactionCommitExecutor.afterCommit(() -> mealProposalAuditService.submitAiAudit(proposalId));
        return rows;
    }

    /**
     * 工具专用提交：只创建提案，不注册 Web 路径的 afterCommit AI 审核。
     *
     * <p>自动审核由 MCP 平台登记为持久化步骤，在真实执行上下文中重新复核授权，
     * 因此这里必须返回创建结果而不触发任何异步副作用。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int submitProposalForTool(MealProposal mealProposal) {
        return createProposal(mealProposal);
    }

    /**
     * 共享的提案创建校验与插入逻辑。
     *
     * @param mealProposal 提案
     * @return 插入行数
     */
    private int createProposal(MealProposal mealProposal) {
        Long deptId = SecurityUtils.getDeptId();
        Long categoryId = mealCategoryGuard.resolveWritableCategoryId(
                mealProposal.getCategoryIds(), deptId, "提案");
        mealProposal.setProposalId(null);
        mealProposal.setDeptId(deptId);
        mealProposal.setUserId(SecurityUtils.getUserId());
        mealProposal.setUserName(currentNickName());
        mealProposal.setStatus(MealProposal.STATUS_PENDING);
        mealProposal.setDishId(null);
        mealProposal.setCreateBy(SecurityUtils.getUsername());
        int rows = mealProposalMapper.insertMealProposal(mealProposal);
        saveProposalCategory(mealProposal.getProposalId(), categoryId);
        return rows;
    }

    /**
     * 人工审核：允许对任意状态（待审核/已通过/已驳回）改判。
     * 通过时若提案尚无菜品则用现有字段新建，已有菜品则重新上架；驳回时把已有菜品下架，均不重复创建菜品。
     *
     * <p>并发裁决使用「期望状态 + 期望数据版本」：同为已通过但被其它审核改写过时，
     * 状态相同也必须拒绝旧快照写入。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int auditProposal(MealProposalAuditBody body) {
        boolean approved = MealProposal.STATUS_APPROVED.equals(body.getStatus());
        boolean rejected = MealProposal.STATUS_REJECTED.equals(body.getStatus());
        if (!approved && !rejected) {
            throw new ServiceException("审核结果只能是 1（通过）或 2（驳回）");
        }

        MealProposal proposal = mealProposalMapper.selectProposalForUpdate(body.getProposalId());
        if (proposal == null) {
            throw new ServiceException("提案不存在或已删除");
        }
        MealScopeGuard.assertSameDept(proposal.getDeptId(), "提案");

        MealProposal update = new MealProposal();
        update.setProposalId(proposal.getProposalId());
        update.setStatus(body.getStatus());
        // 期望状态 + 版本：与 AI 自动审核及其它人工审核并发时只有一个能改动成功
        update.setExpectedStatus(proposal.getStatus());
        update.setExpectedVersion(proposal.getDataVersion());
        update.setAuditBy(currentNickName());
        update.setAuditRemark(body.getAuditRemark());
        update.setUpdateBy(SecurityUtils.getUsername());

        if (approved) {
            if (proposal.getDishId() == null) {
                // 尚未生成菜品：用提案现有字段新建并回写菜品ID
                Long categoryId = mealCategoryRefMapper.selectCategoryId(MealCategoryRef.BIZ_PROPOSAL,
                        proposal.getProposalId());
                update.setDishId(
                        mealProposalAuditService.createDishFromProposal(proposal, categoryId, currentNickName()));
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

    /** 写入提案的唯一主类关联；categoryId 为空则不做任何事 */
    private void saveProposalCategory(Long proposalId, Long categoryId) {
        if (proposalId == null || categoryId == null) {
            return;
        }
        MealCategoryRef ref = new MealCategoryRef();
        ref.setBizType(MealCategoryRef.BIZ_PROPOSAL);
        ref.setBizId(proposalId);
        ref.setCategoryId(categoryId);
        mealCategoryRefMapper.insertMealCategoryRef(ref);
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
