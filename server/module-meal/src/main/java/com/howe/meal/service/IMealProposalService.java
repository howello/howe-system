package com.howe.meal.service;

import com.howe.meal.domain.MealProposal;
import com.howe.meal.domain.dto.MealProposalAuditBody;

import java.util.List;

/**
 * 点餐-新菜提案 服务层
 *
 * @author howe
 */
public interface IMealProposalService {
    /**
     * 查询提案列表（管理端审核，受数据权限约束）
     *
     * @param mealProposal 查询条件
     * @return 提案集合
     */
    List<MealProposal> selectMealProposalList(MealProposal mealProposal);

    /**
     * 查询某个提交人自己的提案
     *
     * @param mealProposal 查询条件
     * @return 提案集合
     */
    List<MealProposal> selectMyProposalList(MealProposal mealProposal);

    /**
     * 提交提案
     *
     * @param mealProposal 提案
     * @return 结果
     */
    int submitProposal(MealProposal mealProposal);

    /**
     * 审核提案，通过时自动生成菜品并回写菜品ID
     *
     * @param body 审核入参
     * @return 结果
     */
    int auditProposal(MealProposalAuditBody body);

    /**
     * 批量删除提案
     *
     * @param proposalIds 提案ID数组
     * @return 结果
     */
    int deleteMealProposalByIds(Long[] proposalIds);
}
