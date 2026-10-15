package com.howe.share.mapper;

import com.howe.share.domain.ShareSource;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * WebDAV 源配置 数据层
 *
 * @author howe
 */
@Mapper
public interface ShareSourceMapper {

    /**
     * 查询源列表
     *
     * @param shareSource 查询条件（name 模糊、status 精确）
     * @return 源集合
     */
    List<ShareSource> selectShareSourceList(ShareSource shareSource);

    /**
     * 按主键查询源
     *
     * @param sourceId 源ID
     * @return 源
     */
    ShareSource selectShareSourceById(Long sourceId);

    /**
     * 查询启用状态的源列表（下拉选择用）
     *
     * @return 源集合
     */
    List<ShareSource> selectEnabledSourceList();

    /**
     * 新增源
     *
     * @param shareSource 源
     * @return 影响行数
     */
    int insertShareSource(ShareSource shareSource);

    /**
     * 修改源
     *
     * @param shareSource 源
     * @return 影响行数
     */
    int updateShareSource(ShareSource shareSource);

    /**
     * 批量删除源
     *
     * @param sourceIds 源ID数组
     * @return 影响行数
     */
    int deleteShareSourceByIds(Long[] sourceIds);
}
