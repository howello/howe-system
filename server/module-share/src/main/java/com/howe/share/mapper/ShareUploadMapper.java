package com.howe.share.mapper;

import com.howe.share.domain.ShareUpload;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

/**
 * 本地上传暂存 数据层
 *
 * @author howe
 */
public interface ShareUploadMapper {

    /**
     * 新增暂存记录
     *
     * @param upload 暂存记录
     * @return 影响行数
     */
    int insertShareUpload(ShareUpload upload);

    /**
     * 按令牌哈希查询
     *
     * @param tokenHash 令牌哈希
     * @return 暂存记录
     */
    ShareUpload selectByTokenHash(@Param("tokenHash") String tokenHash);

    /**
     * 按主键查询
     *
     * @param uploadId 上传ID
     * @return 暂存记录
     */
    ShareUpload selectShareUploadById(@Param("uploadId") Long uploadId);

    /**
     * 条件认领：只有所有者、未过期且仍为 TEMP 的记录才能被认领
     *
     * @param uploadId    上传ID
     * @param ownerUserId 所有者
     * @param linkId      绑定的分享ID
     * @param now         当前时间
     * @return 影响行数，1 表示认领成功
     */
    int claim(@Param("uploadId") Long uploadId,
              @Param("ownerUserId") Long ownerUserId,
              @Param("linkId") Long linkId,
              @Param("now") Date now);

    /**
     * 标记缓存成功（删除暂存字节后调用）
     *
     * @param uploadId 上传ID
     * @return 影响行数
     */
    int markBound(@Param("uploadId") Long uploadId);

    /**
     * 标记过期
     *
     * @param uploadId 上传ID
     * @return 影响行数
     */
    int markExpired(@Param("uploadId") Long uploadId);

    /**
     * 查询可清理的过期上传
     *
     * @param now   当前时间
     * @param limit 单批上限
     * @return 待清理记录
     */
    List<ShareUpload> selectExpiredTemps(@Param("now") Date now, @Param("limit") int limit);

    /**
     * 查询认领后长期未完成的记录（分享已删除或缓存失败到上限）
     *
     * @param before 认领时间早于此值视为可疑
     * @param limit  单批上限
     * @return 待回收记录
     */
    List<ShareUpload> selectStaleClaimed(@Param("before") Date before, @Param("limit") int limit);
}
