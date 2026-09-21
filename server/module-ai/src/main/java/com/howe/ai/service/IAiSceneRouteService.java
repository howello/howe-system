package com.howe.ai.service;

import com.howe.ai.persistence.domain.AiSceneRoute;

import java.util.List;

/**
 * AI 场景路由 服务层
 *
 * @author howe
 */
public interface IAiSceneRouteService
{
    /**
     * 查询场景路由列表
     *
     * @param aiSceneRoute 查询条件
     * @return 路由集合
     */
    List<AiSceneRoute> selectAiSceneRouteList(AiSceneRoute aiSceneRoute);

    /**
     * 新增或修改场景路由
     *
     * <p>同一场景与能力只允许一条路由，重复提交时按已有记录更新。</p>
     *
     * @param aiSceneRoute 场景路由
     * @return 结果
     */
    int saveAiSceneRoute(AiSceneRoute aiSceneRoute);

    /**
     * 批量删除场景路由
     *
     * @param ids 主键ID数组
     * @return 结果
     */
    int deleteAiSceneRouteByIds(Long[] ids);
}
