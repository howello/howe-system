package com.howe.ai.service;

import com.howe.ai.persistence.domain.AiModel;
import com.howe.ai.persistence.domain.dto.AiModelImportRequest;
import com.howe.ai.persistence.domain.vo.AiRemoteModel;

import java.util.List;

/**
 * AI 模型 服务层
 *
 * @author howe
 */
public interface IAiModelService
{
    /**
     * 查询模型列表
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
    AiModel selectAiModelById(Long id);

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
     * @return 结果
     */
    int updateAiModel(AiModel aiModel);

    /**
     * 批量删除模型，被场景路由引用时拒绝
     *
     * @param ids 主键ID数组
     * @return 结果
     */
    int deleteAiModelByIds(Long[] ids);

    /**
     * 启用或停用模型
     *
     * @param id      主键ID
     * @param enabled 0停用 1启用
     * @return 结果
     */
    int updateStatus(Long id, Integer enabled);

    /**
     * 查询渠道对应厂商的可用模型列表
     *
     * @param channelId 渠道ID
     * @return 厂商模型集合，已在本渠道配置的标记为已添加
     */
    List<AiRemoteModel> listRemoteModels(Long channelId);

    /**
     * 批量导入厂商模型
     *
     * @param request 导入请求
     * @return 实际新增的条数，已存在的模型名会被跳过
     */
    int importModels(AiModelImportRequest request);
}
