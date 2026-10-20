package com.howe.meal.mapper;

import com.howe.meal.domain.MealCategoryRef;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 点餐-分类关联 数据层
 *
 * <p>菜品（biz_type=1）与提案（biz_type=2）共用同一张 {@code meal_category_ref}。
 * 主键是 {@code (biz_type, biz_id)}，因此每条业务最多一条分类关联——一道菜只能归一个主类。</p>
 *
 * @author howe
 */
public interface MealCategoryRefMapper {
    /**
     * 写入某条业务的唯一分类关联
     *
     * @param ref bizType/bizId/categoryId 均必填
     * @return 结果
     */
    int insertMealCategoryRef(MealCategoryRef ref);

    /**
     * 清理若干条业务的全部分类关联（物理删除）
     *
     * @param bizType 业务类型
     * @param bizIds  业务ID数组
     * @return 结果
     */
    int deleteMealCategoryRefs(@Param("bizType") Integer bizType, @Param("bizIds") Long[] bizIds);

    /**
     * 查询某条业务已关联的分类ID
     *
     * @param bizType 业务类型
     * @param bizId   业务ID
     * @return 分类ID；未关联返回 null
     */
    Long selectCategoryId(@Param("bizType") Integer bizType, @Param("bizId") Long bizId);

    /**
     * 批量查询多条业务的分类关联（bizId + categoryId 逐行返回），供列表页回填 categoryIds
     *
     * @param bizType 业务类型
     * @param bizIds  业务ID列表
     * @return 关联行列表
     */
    List<MealCategoryRef> selectRefsByBizIds(@Param("bizType") Integer bizType, @Param("bizIds") List<Long> bizIds);

    /**
     * 统计某分类被多少条业务（菜品 + 提案）引用
     *
     * @param categoryId 分类ID
     * @return 引用数
     */
    int countByCategoryId(Long categoryId);
}
