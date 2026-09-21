package com.howe.ai.service;

import com.howe.ai.persistence.domain.AiChannel;
import com.howe.ai.persistence.domain.AiProvider;
import com.howe.ai.provider.AiHealthResult;

import java.util.List;

/**
 * AI 渠道 服务层
 *
 * @author howe
 */
public interface IAiChannelService
{
    /**
     * 查询启用的服务商，供渠道表单的下拉选择使用
     *
     * @return 服务商集合
     */
    List<AiProvider> selectProviderOptions();

    /**
     * 查询渠道列表，密钥以掩码返回
     *
     * @param aiChannel 查询条件
     * @return 渠道集合
     */
    List<AiChannel> selectAiChannelList(AiChannel aiChannel);

    /**
     * 按主键查询渠道，密钥以掩码返回
     *
     * @param id 主键ID
     * @return 渠道
     */
    AiChannel selectAiChannelById(Long id);

    /**
     * 新增渠道，密钥加密入库
     *
     * @param aiChannel 渠道
     * @return 结果
     */
    int insertAiChannel(AiChannel aiChannel);

    /**
     * 修改渠道，密钥为空或掩码时保留原值
     *
     * @param aiChannel 渠道
     * @return 结果
     */
    int updateAiChannel(AiChannel aiChannel);

    /**
     * 批量删除渠道，被模型引用时拒绝
     *
     * @param ids 主键ID数组
     * @return 结果
     */
    int deleteAiChannelByIds(Long[] ids);

    /**
     * 启用或停用渠道
     *
     * @param id      主键ID
     * @param enabled 0停用 1启用
     * @return 结果
     */
    int updateStatus(Long id, Integer enabled);

    /**
     * 连通性检测，并回写健康状态
     *
     * @param id      渠道ID
     * @param modelId 指定模型ID，可为空（为空时取该渠道第一个启用的模型）
     * @return 检测结果
     */
    AiHealthResult testConnection(Long id, Long modelId);
}
