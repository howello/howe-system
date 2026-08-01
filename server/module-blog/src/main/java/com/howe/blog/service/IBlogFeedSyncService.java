package com.howe.blog.service;

import com.howe.blog.domain.vo.BlogFeedSyncResult;

/**
 * 博客朋友圈同步 服务层
 *
 * @author howe
 */
public interface IBlogFeedSyncService {
    /**
     * 同步全部启用的订阅源
     *
     * @return 同步结果
     */
    public BlogFeedSyncResult syncAll();

    /**
     * 同步单个订阅源
     *
     * @param linkId 订阅源ID
     * @return 同步结果
     */
    public BlogFeedSyncResult syncOne(Long linkId);
}
