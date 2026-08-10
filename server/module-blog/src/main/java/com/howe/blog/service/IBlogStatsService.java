package com.howe.blog.service;

import com.howe.blog.domain.vo.BlogHomeStats;

/**
 * 博客首页统计服务
 *
 * <p>聚合口径（文章含隐藏、草稿仅 status=0、说说全量、30 天窗口、
 * 分类 Top7+其他）与降级（blog 表缺失置 blogAvailable=false）
 * 一律封装在本服务内，表结构知识不外泄到 admin 层。</p>
 *
 * @author howe
 */
public interface IBlogStatsService {
    /**
     * 获取首页博客聚合统计（含降级标志）；结果走 Redis 缓存
     *
     * @return 首页博客聚合统计；blog 表缺失时为 {@code BlogHomeStats(false, null)}
     */
    BlogHomeStats getHomeStats();
}