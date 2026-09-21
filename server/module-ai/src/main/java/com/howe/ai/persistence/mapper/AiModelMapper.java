package com.howe.ai.persistence.mapper;

import com.howe.ai.persistence.domain.AiModel;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * AI 模型 数据层
 *
 * @author howe
 */
public interface AiModelMapper
{
    /**
     * 查询模型列表（联表带出归属渠道名称与服务商标识）
     *
     * @param aiModel 查询条件
     * @return 模型集合
     */
    List<AiModel> selectAiModelList(AiModel aiModel);

    /**
     * 按主键查询模型
     *
     * @param id 主键ID
     * @return 模型
     */
    AiModel selectAiModelById(@Param("id") Long id);

    /**
     * 查询全部启用的模型，供配置缓存加载
     *
     * @return 模型集合
     */
    List<AiModel> selectEnabledList();

    /**
     * 统计引用了指定渠道的模型数
     *
     * @param channelId 渠道ID
     * @return 模型数
     */
    int countByChannelId(@Param("channelId") Long channelId);

    /**
     * 新增模型
     *
     * @param aiModel 模型
     * @return 结果
     */
    int insertAiModel(AiModel aiModel);

    /**
     * 修改模型
     *
     * @param aiModel 模型
     * @return 影响行数
     */
    int updateAiModel(AiModel aiModel);

    /**
     * 按主键批量删除模型
     *
     * @param ids 主键ID数组
     * @return 结果
     */
    int deleteAiModelByIds(@Param("ids") Long[] ids);
}
