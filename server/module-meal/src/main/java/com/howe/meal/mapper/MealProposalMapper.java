package com.howe.meal.mapper;

import com.howe.meal.domain.MealProposal;

import java.util.List;

/**
 * 点餐-新菜提案 数据层
 *
 * <p>提案是纯家庭私有数据，list 查询走 {@code @DataScope}；
 * 提交人的「我的提案」另有独立方法强制按 {@code user_id} 过滤。</p>
 *
 * @author howe
 */
public interface MealProposalMapper {
    /**
     * 查询提案列表（配合 {@code @DataScope} 使用，管理端审核）
     *
     * @param mealProposal 查询条件
     * @return 提案集合
     */
    List<MealProposal> selectMealProposalList(MealProposal mealProposal);

    /**
     * 查询某个提交人自己的提案
     *
     * @param mealProposal 查询条件，userId 必填
     * @return 提案集合
     */
    List<MealProposal> selectMyProposalList(MealProposal mealProposal);

    /**
     * 按主键查询提案
     *
     * @param proposalId 提案ID
     * @return 提案
     */
    MealProposal selectMealProposalById(Long proposalId);

    /**
     * 新增提案
     *
     * @param mealProposal 提案
     * @return 结果
     */
    int insertMealProposal(MealProposal mealProposal);

    /**
     * 修改提案（审核结果与回写的菜品ID）
     *
     * @param mealProposal 提案
     * @return 结果
     */
    int updateMealProposal(MealProposal mealProposal);

    /**
     * 批量删除提案（逻辑删除）
     *
     * @param proposalIds 提案ID数组
     * @return 结果
     */
    int deleteMealProposalByIds(Long[] proposalIds);
}
