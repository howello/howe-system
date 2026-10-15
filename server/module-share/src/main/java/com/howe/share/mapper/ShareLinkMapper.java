package com.howe.share.mapper;

import com.howe.share.domain.ShareLink;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 分享记录 数据层
 *
 * @author howe
 */
@Mapper
public interface ShareLinkMapper {

    /**
     * 查询分享列表（连带源名称）
     *
     * @param shareLink 查询条件（fileName 模糊、status / cacheStatus / sourceId 精确）
     * @return 分享集合
     */
    List<ShareLink> selectShareLinkList(ShareLink shareLink);

    /**
     * 按主键查询分享
     *
     * @param linkId 分享ID
     * @return 分享
     */
    ShareLink selectShareLinkById(Long linkId);

    /**
     * 按短码查询分享
     *
     * @param code 短码
     * @return 分享
     */
    ShareLink selectShareLinkByCode(String code);

    /**
     * 新增分享
     *
     * @param shareLink 分享
     * @return 影响行数
     */
    int insertShareLink(ShareLink shareLink);

    /**
     * 修改分享（约束字段）
     *
     * @param shareLink 分享
     * @return 影响行数
     */
    int updateShareLink(ShareLink shareLink);

    /**
     * 批量删除分享
     *
     * @param linkIds 分享ID数组
     * @return 影响行数
     */
    int deleteShareLinkByIds(Long[] linkIds);

    /**
     * 原子计数 +1
     *
     * <p>仅当链接正常（status='0'）且未超查看次数时生效；受影响行数为 1 才算一次有效访问。
     * {@code max_views <= 0} 视为不限次数。</p>
     *
     * @param linkId 分享ID
     * @return 影响行数
     */
    int incrementViewCount(@Param("linkId") Long linkId);

    /**
     * 按条件更新缓存状态（CAS，防重复领取）
     *
     * @param linkId 分享ID
     * @param from   期望的原状态
     * @param to     目标状态
     * @return 影响行数
     */
    int updateCacheStatus(@Param("linkId") Long linkId, @Param("from") String from, @Param("to") String to);

    /**
     * 标记缓存就绪：写入存储落点并置 cache_status=2
     *
     * @param linkId          分享ID
     * @param storageType     存储位置（0本地 1R2）
     * @param storageLocation 存储地址
     * @param fileSize        字节数
     * @param contentType     MIME 类型
     * @return 影响行数
     */
    int markCacheReady(@Param("linkId") Long linkId, @Param("storageType") String storageType,
                       @Param("storageLocation") String storageLocation, @Param("fileSize") Long fileSize,
                       @Param("contentType") String contentType);

    /**
     * 标记缓存失败：置 cache_status=3、记录原因、重试次数 +1
     *
     * @param linkId 分享ID
     * @param error  失败原因
     * @return 影响行数
     */
    int markCacheFailed(@Param("linkId") Long linkId, @Param("error") String error);

    /**
     * 标记已焚毁（逻辑焚毁，status=2）
     *
     * @param linkId 分享ID
     * @return 影响行数
     */
    int markBurned(@Param("linkId") Long linkId);

    /**
     * 标记已失效 / 恢复（仅改 status）
     *
     * @param linkId 分享ID
     * @param status 目标状态（0正常 1已失效）
     * @return 影响行数
     */
    int updateStatus(@Param("linkId") Long linkId, @Param("status") String status);

    /**
     * 重置缓存状态为待缓存（手动重新缓存用）
     *
     * @param linkId 分享ID
     * @return 影响行数
     */
    int resetCacheStatus(@Param("linkId") Long linkId);

    /**
     * 恢复分享时重置访问计数与缓存状态（view_count 归零、缓存重新入队）
     *
     * @param linkId 分享ID
     * @return 影响行数
     */
    int resetForRestore(@Param("linkId") Long linkId);

    /**
     * 查询需要重新入队缓存的分享（cache_status=3 且未超重试上限）
     *
     * @param maxRetry 最大重试次数
     * @param limit    单批上限
     * @return 分享集合
     */
    List<ShareLink> selectCacheRetryList(@Param("maxRetry") int maxRetry, @Param("limit") int limit);

    /**
     * 查询需要清理的分享（已焚毁，或已过期且仍正常）
     *
     * @param limit 单批上限
     * @return 分享集合
     */
    List<ShareLink> selectCleanupList(@Param("limit") int limit);

    /**
     * 查询全部短码（清理本地残留文件时用于比对）
     *
     * @return 短码集合
     */
    List<String> selectAllCodes();

    /**
     * 统计某源下的分享数量（删除源前的占用校验）
     *
     * @param sourceId 源ID
     * @return 数量
     */
    long countBySourceId(@Param("sourceId") Long sourceId);
}
