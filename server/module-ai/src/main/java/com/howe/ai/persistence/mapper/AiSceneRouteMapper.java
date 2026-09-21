package com.howe.ai.persistence.mapper;

import com.howe.ai.persistence.domain.AiSceneRoute;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * AI 场景路由 数据层
 *
 * @author howe
 */
public interface AiSceneRouteMapper
{
    /**
     * 查询场景路由列表（联表带出主模型信息）
     *
     * @param aiSceneRoute 查询条件
     * @return 路由集合
     */
    List<AiSceneRoute> selectAiSceneRouteList(AiSceneRoute aiSceneRoute);

    /**
     * 按主键查询场景路由
     *
     * @param id 主键ID
     * @return 场景路由
     */
    AiSceneRoute selectAiSceneRouteById(@Param("id") Long id);

    /**
     * 按场景与能力查询路由
     *
     * @param scene      场景标识
     * @param capability 能力类型
     * @return 场景路由，不存在时返回 null
     */
    AiSceneRoute selectByScene(@Param("scene") String scene, @Param("capability") String capability);

    /**
     * 查询全部启用的场景路由，供配置缓存加载
     *
     * @return 路由集合
     */
    List<AiSceneRoute> selectEnabledList();

    /**
     * 统计引用了指定模型的路由数
     *
     * @param modelId 模型ID
     * @return 路由数
     */
    int countByModelId(@Param("modelId") Long modelId);

    /**
     * 新增场景路由
     *
     * @param aiSceneRoute 场景路由
     * @return 结果
     */
    int insertAiSceneRoute(AiSceneRoute aiSceneRoute);

    /**
     * 修改场景路由
     *
     * @param aiSceneRoute 场景路由
     * @return 影响行数
     */
    int updateAiSceneRoute(AiSceneRoute aiSceneRoute);

    /**
     * 按主键批量删除场景路由
     *
     * @param ids 主键ID数组
     * @return 结果
     */
    int deleteAiSceneRouteByIds(@Param("ids") Long[] ids);
}
