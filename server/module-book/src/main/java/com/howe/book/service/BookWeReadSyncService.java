package com.howe.book.service;

import com.howe.book.domain.BookWeReadStatus;

/** 管理微信读书共享书架的手动和定时导入。 */
public interface BookWeReadSyncService {
    /** 查询共享书架的同步状态。 */
    BookWeReadStatus status();

    /** 提交手动任务，已有任务进行时返回当前任务。 */
    BookWeReadStatus start(long userId);
}
