package com.howe.ai.persistence.mapper;

import com.howe.ai.persistence.domain.AiImageAsset;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * AI 生成图资产 数据层
 *
 * @author howe
 */
public interface AiImageAssetMapper
{
    /**
     * 新增生成图资产
     *
     * @param aiImageAsset 生成图资产
     * @return 结果
     */
    int insertAiImageAsset(AiImageAsset aiImageAsset);

    /**
     * 按调用记录 ID 查询生成图资产
     *
     * @param callLogId 调用记录ID
     * @return 资产集合
     */
    List<AiImageAsset> selectByCallLogId(@Param("callLogId") Long callLogId);
}
