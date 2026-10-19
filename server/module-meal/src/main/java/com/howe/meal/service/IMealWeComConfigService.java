package com.howe.meal.service;

import com.howe.meal.domain.MealWeComConfig;

import java.util.List;

/**
 * 点餐-企业微信通知配置 服务层
 *
 * @author howe
 */
public interface IMealWeComConfigService {

    /**
     * 查询配置列表
     *
     * <p>平台管理员返回全部家庭；其他登录用户只返回本家庭。</p>
     *
     * @param config 查询条件
     * @return 配置集合
     */
    List<MealWeComConfig> selectMealWeComConfigList(MealWeComConfig config);

    /**
     * 按主键查询配置（key 为掩码）
     *
     * @param configId 配置ID
     * @return 配置
     */
    MealWeComConfig selectMealWeComConfigById(Long configId);

    /**
     * 新增配置
     *
     * @param config 配置
     * @return 结果
     */
    int insertMealWeComConfig(MealWeComConfig config);

    /**
     * 修改配置
     *
     * @param config 配置
     * @return 结果
     */
    int updateMealWeComConfig(MealWeComConfig config);

    /**
     * 批量删除配置（逻辑删除）
     *
     * @param configIds 配置ID数组
     * @return 结果
     */
    int deleteMealWeComConfigByIds(Long[] configIds);

    /**
     * 向指定配置对应的企业微信群发送测试消息
     *
     * @param configId 配置ID
     * @return 成功或失败的可读结果
     */
    String sendTestMessage(Long configId);
}
