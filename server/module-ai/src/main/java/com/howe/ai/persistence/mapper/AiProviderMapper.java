package com.howe.ai.persistence.mapper;

import com.howe.ai.persistence.domain.AiProvider;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * AI 服务商 数据层
 *
 * @author howe
 */
public interface AiProviderMapper
{
    /**
     * 查询服务商列表
     *
     * @param aiProvider 查询条件
     * @return 服务商集合
     */
    List<AiProvider> selectAiProviderList(AiProvider aiProvider);

    /**
     * 按主键查询服务商
     *
     * @param id 主键ID
     * @return 服务商
     */
    AiProvider selectAiProviderById(@Param("id") Long id);

    /**
     * 按标识查询服务商
     *
     * @param code 服务商标识
     * @return 服务商
     */
    AiProvider selectAiProviderByCode(@Param("code") String code);

    /**
     * 查询全部启用的服务商，供配置缓存加载
     *
     * @return 服务商集合
     */
    List<AiProvider> selectEnabledList();

    /**
     * 新增服务商
     *
     * @param aiProvider 服务商
     * @return 结果
     */
    int insertAiProvider(AiProvider aiProvider);

    /**
     * 修改服务商
     *
     * @param aiProvider 服务商
     * @return 影响行数
     */
    int updateAiProvider(AiProvider aiProvider);

    /**
     * 按主键批量删除服务商
     *
     * @param ids 主键ID数组
     * @return 结果
     */
    int deleteAiProviderByIds(@Param("ids") Long[] ids);
}
