package com.howe.meal.mapper;

import com.howe.meal.domain.MealAppVersion;

import java.util.List;

/**
 * 点餐-App 版本 数据层
 *
 * <p>版本记录是平台级配置（不分家庭），列表与检测都直接查全表，不走 {@code @DataScope}。</p>
 *
 * @author howe
 */
public interface MealAppVersionMapper {

    /**
     * 查询版本列表
     *
     * @param mealAppVersion 查询条件（versionName 模糊、status 精确）
     * @return 版本集合
     */
    List<MealAppVersion> selectMealAppVersionList(MealAppVersion mealAppVersion);

    /**
     * 按主键查询版本
     *
     * @param versionId 版本记录ID
     * @return 版本
     */
    MealAppVersion selectMealAppVersionById(Long versionId);

    /**
     * 查询已发布的最新版本
     *
     * <p>取 {@code del_flag='0' and status='0'} 中 {@code version_code} 最大的一条；
     * 并列时取创建时间最新的一条。</p>
     *
     * @return 最新已发布版本；没有时返回 {@code null}
     */
    MealAppVersion selectLatestPublished();

    /**
     * 新增版本
     *
     * @param mealAppVersion 版本
     * @return 结果
     */
    int insertMealAppVersion(MealAppVersion mealAppVersion);

    /**
     * 修改版本
     *
     * @param mealAppVersion 版本
     * @return 结果
     */
    int updateMealAppVersion(MealAppVersion mealAppVersion);

    /**
     * 批量删除版本（逻辑删除）
     *
     * @param versionIds 版本记录ID数组
     * @return 结果
     */
    int deleteMealAppVersionByIds(Long[] versionIds);
}
