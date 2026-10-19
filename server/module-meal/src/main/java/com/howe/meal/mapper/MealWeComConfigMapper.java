package com.howe.meal.mapper;

import com.howe.meal.domain.MealWeComConfig;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 点餐-企业微信通知配置 数据层
 *
 * <p>配置按家庭隔离：平台管理员看全部，家庭管理员只看本家庭；
 * 归属校验在 Service 层用 {@code MealScopeGuard} 完成。</p>
 *
 * @author howe
 */
public interface MealWeComConfigMapper {

    /**
     * 查询配置列表
     *
     * @param config 查询条件，支持 deptId 精确过滤
     * @return 配置集合
     */
    List<MealWeComConfig> selectMealWeComConfigList(MealWeComConfig config);

    /**
     * 按主键查询配置
     *
     * @param configId 配置ID
     * @return 配置
     */
    MealWeComConfig selectMealWeComConfigById(Long configId);

    /**
     * 按家庭查询有效配置
     *
     * @param deptId 家庭ID
     * @return 配置，不存在时返回 null
     */
    MealWeComConfig selectByDeptId(@Param("deptId") Long deptId);

    /**
     * 按家庭查询配置（包含已逻辑删除的记录）
     *
     * <p>家庭与配置是一对一关系，`dept_id` 有唯一索引；删除后重新新增时复用原行，
     * 避免唯一约束冲突。</p>
     *
     * @param deptId 家庭ID
     * @return 配置，不存在时返回 null
     */
    MealWeComConfig selectAnyByDeptId(@Param("deptId") Long deptId);

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
     * 更新 webhook key（仅在用户提交新值时调用）
     *
     * @param configId   配置ID
     * @param webhookKey webhook key
     * @param updateBy   更新人
     * @return 结果
     */
    int updateWebhookKey(@Param("configId") Long configId, @Param("webhookKey") String webhookKey,
            @Param("updateBy") String updateBy);

    /**
     * 恢复一条已逻辑删除的配置，并用新值覆盖
     *
     * @param config 配置
     * @return 结果
     */
    int restoreMealWeComConfig(MealWeComConfig config);

    /**
     * 批量逻辑删除
     *
     * @param configIds 配置ID数组
     * @return 结果
     */
    int deleteMealWeComConfigByIds(Long[] configIds);
}
