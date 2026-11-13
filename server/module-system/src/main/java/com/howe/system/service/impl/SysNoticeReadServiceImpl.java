package com.howe.system.service.impl;

import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.howe.system.domain.SysNotice;
import com.howe.system.domain.SysNoticeRead;
import com.howe.system.mapper.SysNoticeReadMapper;
import com.howe.system.service.ISysNoticeReadService;
import com.howe.system.service.SysNoticeReadBatchResult;

/**
 * 公告已读记录 服务层实现
 *
 * @author howe
 */
@Service
public class SysNoticeReadServiceImpl implements ISysNoticeReadService
{
    @Autowired
    private SysNoticeReadMapper noticeReadMapper;

    /**
     * 标记已读
     */
    @Override
    public void markRead(Long noticeId, Long userId)
    {
        SysNoticeRead record = new SysNoticeRead();
        record.setNoticeId(noticeId);
        record.setUserId(userId);
        noticeReadMapper.insertNoticeRead(record);
    }

    /**
     * 查询某用户未读公告数量
     */
    @Override
    public int selectUnreadCount(Long userId)
    {
        return noticeReadMapper.selectUnreadCount(userId);
    }

    /**
     * 查询公告列表并标记当前用户已读状态
     */
    @Override
    public List<SysNotice> selectNoticeListWithReadStatus(Long userId, int limit)
    {
        return noticeReadMapper.selectNoticeListWithReadStatus(userId, limit);
    }

    /**
     * 工具专用的本人公告分页查询。
     *
     * <p>旧首页查询保留 {@code limit} 语义不变；本方法在主查询处执行数据库分页，
     * 已读筛选下推到 SQL，不在内存里过滤或切片。</p>
     */
    @Override
    public List<SysNotice> selectNoticePageWithReadStatus(Long userId, String readState, int page, int pageSize)
    {
        String state = readState == null || readState.isBlank() ? "all" : readState;
        if (!"all".equals(state) && !"read".equals(state) && !"unread".equals(state))
        {
            throw new com.howe.common.exception.ServiceException("不支持的已读筛选：" + readState);
        }
        com.github.pagehelper.PageHelper.startPage(page, pageSize);
        try
        {
            return noticeReadMapper.selectNoticePageWithReadStatus(userId, state);
        }
        finally
        {
            com.github.pagehelper.PageHelper.clearPage();
        }
    }

    /**
     * 批量标记已读
     */
    @Override
    public void markReadBatch(Long userId, Long[] noticeIds)
    {
        if (noticeIds == null || noticeIds.length == 0)
        {
            return;
        }
        noticeReadMapper.insertNoticeReadBatch(userId, noticeIds);
    }

    /**
     * 工具专用的受控批量已读。
     *
     * <p>同一事务内先锁定并校验整批公告均为正常状态，再读取已读集合并写入未读项；
     * 任何公告不存在或已停用都整批拒绝，不产生部分写入。</p>
     */
    @Override
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public SysNoticeReadBatchResult markReadableBatchForTool(Long userId, java.util.List<Long> noticeIds)
    {
        if (userId == null || userId <= 0 || noticeIds == null || noticeIds.isEmpty())
        {
            throw new com.howe.common.exception.ServiceException("公告已读参数不合法");
        }
        Long[] ids = noticeIds.stream().distinct().sorted().toArray(Long[]::new);
        int readable = noticeReadMapper.countReadableForUpdate(ids);
        if (readable != ids.length)
        {
            throw new com.howe.common.exception.ServiceException("包含不存在或已停用的公告");
        }
        java.util.Set<Long> already = new java.util.HashSet<>(noticeReadMapper.selectReadNoticeIds(userId, ids));
        java.util.List<Long> pending = java.util.Arrays.stream(ids).filter(id -> !already.contains(id)).toList();
        if (!pending.isEmpty())
        {
            noticeReadMapper.insertNoticeReadBatch(userId, pending.toArray(Long[]::new));
        }
        return new SysNoticeReadBatchResult(pending, new java.util.ArrayList<>(already));
    }

    /**
     * 查询已阅读某公告的用户列表
     */
    @Override
    public List<Map<String, Object>> selectReadUsersByNoticeId(Long noticeId, String searchValue)
    {
        return noticeReadMapper.selectReadUsersByNoticeId(noticeId, searchValue);
    }

    /**
     * 删除公告时清理对应已读记录
     */
    @Override
    public void deleteByNoticeIds(Long[] noticeIds)
    {
        noticeReadMapper.deleteByNoticeIds(noticeIds);
    }
}
