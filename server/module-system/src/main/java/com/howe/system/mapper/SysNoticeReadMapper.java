package com.howe.system.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;
import com.howe.system.domain.SysNotice;
import com.howe.system.domain.SysNoticeRead;

/**
 * 公告已读记录 数据层
 *
 * @author howe
 */
public interface SysNoticeReadMapper
{
    /**
     * 新增已读记录（忽略重复）
     *
     * @param noticeRead 已读记录
     * @return 结果
     */
    public int insertNoticeRead(SysNoticeRead noticeRead);

    /**
     * 查询某用户未读公告数量
     *
     * @param userId 用户ID
     * @return 未读数量
     */
    public int selectUnreadCount(@Param("userId") Long userId);

    /**
     * 查询某用户是否已读某公告
     *
     * @param noticeId 公告ID
     * @param userId   用户ID
     * @return 已读记录数（0未读 1已读）
     */
    public int selectIsRead(@Param("noticeId") Long noticeId, @Param("userId") Long userId);

    /**
     * 批量标记已读
     *
     * @param userId    用户ID
     * @param noticeIds 公告ID数组
     * @return 结果
     */
    public int insertNoticeReadBatch(@Param("userId") Long userId, @Param("noticeIds") Long[] noticeIds);

    /**
     * 锁定并校验一批公告是否全部为正常可读状态。
     *
     * @param noticeIds 公告ID数组，按升序传入
     * @return 正常公告数量
     */
    public int countReadableForUpdate(@Param("noticeIds") Long[] noticeIds);

    /**
     * 查询当前用户在一批公告中已读的公告ID。
     *
     * @param userId 用户ID
     * @param noticeIds 公告ID数组
     * @return 已读公告ID
     */
    public java.util.List<Long> selectReadNoticeIds(@Param("userId") Long userId,
            @Param("noticeIds") Long[] noticeIds);

    /**
     * 查询带已读状态的公告列表（SQL层限制条数，一次查询完成）
     *
     * @param userId 用户ID
     * @param limit  最多返回条数
     * @return 带 isRead 标记的公告列表
     */
    public List<SysNotice> selectNoticeListWithReadStatus(@Param("userId") Long userId, @Param("limit") int limit);

    /**
     * 工具专用的本人公告查询，不带内置条数限制，由调用方分页。
     *
     * @param userId 当前用户ID
     * @param readState 已读筛选：{@code all}/{@code read}/{@code unread}
     * @return 公告列表
     */
    public List<SysNotice> selectNoticePageWithReadStatus(@Param("userId") Long userId,
            @Param("readState") String readState);

    /**
     * 查询已阅读某公告的用户列表
     *
     * @param noticeId 公告ID
     * @param searchValue 搜索值
     * @return 已读用户列表
     */
    public List<Map<String, Object>> selectReadUsersByNoticeId(@Param("noticeId") Long noticeId, @Param("searchValue") String searchValue);

    /**
     * 公告删除时清理对应已读记录
     *
     * @param noticeIds 公告ID数组
     * @return 结果
     */
    public int deleteByNoticeIds(@Param("noticeIds") Long[] noticeIds);
}
