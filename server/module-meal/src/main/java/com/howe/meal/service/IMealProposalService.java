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
     * 查询提案详情
     *
     * <p>「按 id 取详情」不受 {@code @DataScope} 保护，Service 层显式校验家庭归属。
     * 返回提交时填写的全部字段、AI 补齐后的菜谱字段与审核结论。</p>
     *
     * @param proposalId 提案ID
     * @return 提案详情
     */
    MealProposal selectMealProposalDetail(Long proposalId);

    /**
     * 提交提案
     *
     * @param mealProposal 提案
     * @return 结果
     */
    int submitProposal(MealProposal mealProposal);

    /**
     * 人工审核提案：允许对任意状态改判。
     * 通过时若无菜品则新建、已有菜品则重新上架；驳回时把已有菜品下架，均不重复创建菜品。
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
