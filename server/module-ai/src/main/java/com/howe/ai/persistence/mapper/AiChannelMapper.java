package com.howe.ai.persistence.mapper;

import com.howe.ai.persistence.domain.AiChannel;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

/**
 * AI 渠道 数据层
 *
 * @author howe
 */
public interface AiChannelMapper
{
    /**
     * 查询渠道列表
     *
     * @param aiChannel 查询条件
     * @return 渠道集合
     */
    List<AiChannel> selectAiChannelList(AiChannel aiChannel);

    /**
     * 按主键查询渠道
     *
     * @param id 主键ID
     * @return 渠道
     */
    AiChannel selectAiChannelById(@Param("id") Long id);

    /**
     * 查询全部启用的渠道，供配置缓存加载
     *
     * @return 渠道集合
     */
    List<AiChannel> selectEnabledList();

    /**
     * 统计引用了指定服务商的渠道数
     *
     * @param providerCode 服务商标识
     * @return 渠道数
     */
    int countByProviderCode(@Param("providerCode") String providerCode);

    /**
     * 新增渠道
     *
     * @param aiChannel 渠道
     * @return 结果
     */
    int insertAiChannel(AiChannel aiChannel);

    /**
     * 修改渠道
     *
     * @param aiChannel 渠道
     * @return 影响行数
     */
    int updateAiChannel(AiChannel aiChannel);

    /**
     * 只更新密钥，避免编辑时把其它字段一起覆盖
     *
     * @param id       渠道ID
     * @param apiKey   密文密钥
     * @param updateBy 更新者
     * @return 影响行数
     */
    int updateApiKey(@Param("id") Long id, @Param("apiKey") String apiKey, @Param("updateBy") String updateBy);

    /**
     * 更新健康状态与检测时间
     *
     * @param id           渠道ID
     * @param healthStatus 健康状态
     * @param lastCheckAt  检测时间
     * @return 影响行数
     */
    int updateHealth(@Param("id") Long id, @Param("healthStatus") String healthStatus,
            @Param("lastCheckAt") Date lastCheckAt);

    /**
     * 按主键批量删除渠道
     *
     * @param ids 主键ID数组
     * @return 结果
     */
    int deleteAiChannelByIds(@Param("ids") Long[] ids);
}
