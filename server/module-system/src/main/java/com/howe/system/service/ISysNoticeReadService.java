package com.howe.system.service;

import java.util.List;
import java.util.Map;
import com.howe.system.domain.SysNotice;

/**
 * 公告已读记录 服务层
 *
 * @author howe
 */
public interface ISysNoticeReadService
{
    /**
     * 标记已读（幂等，重复调用不报错）
     *
     * @param noticeId 公告ID
     * @param userId   用户ID
     */
    public void markRead(Long noticeId, Long userId);

    /**
     * 查询某用户未读公告数量
     *
     * @param userId 用户ID
     * @return 未读数量
     */
    public int selectUnreadCount(Long userId);

    /**
     * 查询公告列表并标记当前用户已读状态（用于首页展示）
     *
     * @param userId 用户ID
     * @param limit  最多返回条数
     * @return 带 isRead 标记的公告列表
     */
    public List<SysNotice> selectNoticeListWithReadStatus(Long userId, int limit);

    /**
     * 工具专用的本人公告分页查询。
     *
     * <p>不沿用首页的 {@code limit} 语义：主查询不带内置条数限制，分页由调用方在
     * 主查询处执行；过滤条件为正常状态公告 + 当前用户已读关联。</p>
     *
     * @param userId 当前用户ID
     * @param readState 已读筛选：{@code all}、{@code read}、{@code unread}
     * @param page 页码，从 1 开始
     * @param pageSize 每页条数
     * @return 当前页公告
     */
    public List<SysNotice> selectNoticePageWithReadStatus(Long userId, String readState, int page, int pageSize);

    /**
     * 批量标记已读
     *
     * @param userId    用户ID
     * @param noticeIds 公告ID数组
     */
    public void markReadBatch(Long userId, Long[] noticeIds);

    /**
     * 工具专用的受控批量已读：先校验整批公告均为正常可读，再写入。
     *
     * <p>任何非法公告导致整批拒绝，不产生部分写入；已在同一事务内锁定公告，
     * 避免校验后被并发停用。旧 {@link #markReadBatch(Long, Long[])} 不提供该校验，
     * 工具入口不能直接调用它。</p>
     *
     * @param userId 当前用户ID
     * @param noticeIds 公告ID列表，调用方已去重并升序
     * @return 新写入与已读集合
     */
    public SysNoticeReadBatchResult markReadableBatchForTool(Long userId, java.util.List<Long> noticeIds);

    /**
     * 查询已阅读某公告的用户列表
     *
     * @param noticeId  公告ID
     * @param searchValue 搜索值
     * @return 已读用户列表
     */
    public List<Map<String, Object>> selectReadUsersByNoticeId(Long noticeId, String searchValue);

    /**
     * 删除公告时清理对应已读记录
     *
     * @param noticeIds 公告ID数组
     */
    public void deleteByNoticeIds(Long[] noticeIds);
}
