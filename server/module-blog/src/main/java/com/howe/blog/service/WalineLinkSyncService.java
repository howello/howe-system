package com.howe.blog.service;

/**
 * waline 友链同步服务
 *
 * @author howe
 */
public interface WalineLinkSyncService {

    /**
     * 同步结果
     *
     * @param newCount   本次新增条数
     * @param skipCount  本次跳过条数（窗口外 + 已存在）
     * @param pageCount  翻页数
     */
    record WalineLinkSyncResult(int newCount, int skipCount, int pageCount) {}

    /**
     * 同步友链申请留言
     *
     * @param windowMinutes 时间窗口（分钟），null 时默认 30
     * @return 同步结果
     */
    WalineLinkSyncResult sync(Integer windowMinutes);
}
