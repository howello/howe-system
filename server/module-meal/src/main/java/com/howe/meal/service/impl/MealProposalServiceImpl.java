package com.howe.meal.service.impl;

import com.howe.common.annotation.DataScope;
import com.howe.common.core.domain.model.LoginUser;
import com.howe.common.exception.ServiceException;
import com.howe.common.utils.SecurityUtils;
import com.howe.common.utils.StringUtils;
import com.howe.meal.domain.MealDish;
import com.howe.meal.domain.MealProposal;
import com.howe.meal.domain.dto.MealProposalAuditBody;
import com.howe.meal.mapper.MealDishMapper;
import com.howe.meal.mapper.MealProposalMapper;
import com.howe.meal.service.IMealProposalService;
import com.howe.meal.util.MealScopeGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 点餐-新菜提案 服务层实现
 *
 * @author howe
 */
@Service
@RequiredArgsConstructor
public class MealProposalServiceImpl implements IMealProposalService {

    private final MealProposalMapper mealProposalMapper;
    private final MealDishMapper mealDishMapper;

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
    public int submitProposal(MealProposal mealProposal) {
        mealProposal.setProposalId(null);
        mealProposal.setDeptId(SecurityUtils.getDeptId());
        mealProposal.setUserId(SecurityUtils.getUserId());
        mealProposal.setUserName(currentNickName());
        mealProposal.setStatus(MealProposal.STATUS_PENDING);
        mealProposal.setDishId(null);
        mealProposal.setCreateBy(SecurityUtils.getUsername());
        return mealProposalMapper.insertMealProposal(mealProposal);
    }

    /**
     * 审核通过时自动往 meal_dish 落一条菜品（source = 1），并把生成的 dishId 回写提案，
     * 让「已通过」的提案能直接指向点餐区里的那道菜。
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
        if (!SecurityUtils.isAdmin()) {
            MealScopeGuard.assertSameDept(proposal.getDeptId(), "提案");
        }
        if (!MealProposal.STATUS_PENDING.equals(proposal.getStatus())) {
            throw new ServiceException("该提案已经审核过了");
        }

        MealProposal update = new MealProposal();
        update.setProposalId(proposal.getProposalId());
        update.setStatus(body.getStatus());
        update.setAuditBy(currentNickName());
        update.setAuditRemark(body.getAuditRemark());
        update.setUpdateBy(SecurityUtils.getUsername());

        if (approved) {
            MealDish dish = new MealDish();
            dish.setDeptId(proposal.getDeptId());
            dish.setCategoryId(proposal.getCategoryId());
            dish.setName(proposal.getName());
            dish.setCover(proposal.getImage());
            dish.setDescription(proposal.getDescription());
            dish.setStatus("0");
            dish.setSort(0);
            dish.setSource("1");
            dish.setCreateBy(SecurityUtils.getUsername());
            mealDishMapper.insertMealDish(dish);
            update.setDishId(dish.getDishId());
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
            if (!SecurityUtils.isAdmin()) {
                MealScopeGuard.assertSameDept(exist.getDeptId(), "提案");
            }
        }
        return mealProposalMapper.deleteMealProposalByIds(proposalIds);
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
