package com.howe.share.mapper;

import com.howe.share.domain.ShareAccessLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

/**
 * 分享访问日志 数据层
 *
 * @author howe
 */
@Mapper
public interface ShareAccessLogMapper {

    /**
     * 新增访问日志
     *
     * @param shareAccessLog 日志
     * @return 影响行数
     */
    int insertShareAccessLog(ShareAccessLog shareAccessLog);

    /**
     * 按分享ID查询访问记录
     *
     * @param linkId 分享ID
     * @return 日志集合
     */
    List<ShareAccessLog> selectShareAccessLogByLinkId(Long linkId);

    /**
     * 删除指定时间之前的访问日志
     *
     * @param before 截止时间（早于该时间的记录被删除）
     * @return 影响行数
     */
    int deleteShareAccessLogBefore(@Param("before") Date before);

    /**
     * 删除指定分享的访问日志（删除分享时连带清理）
     *
     * @param linkIds 分享ID数组
     * @return 影响行数
     */
    int deleteShareAccessLogByLinkIds(Long[] linkIds);
}
