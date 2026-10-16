package com.howe.share.service;

import com.howe.share.domain.vo.ShareCacheHealth;
import com.howe.share.domain.vo.ShareHomeStats;

/**
 * 分享首页统计服务
 *
 * <p>分享是平台级数据（不分家庭），统计口径封装在本服务内。</p>
 *
 * @author howe
 */
public interface IShareStatsService {

    /**
     * 首页分享统计（链接总数、今日访问与下载、最近创建的链接）
     *
     * @return 分享统计
     */
    ShareHomeStats getHomeStats();

    /**
     * 缓存健康（就绪 / 待缓存 / 失败）
     *
     * @return 缓存健康
     */
    ShareCacheHealth getCacheHealth();

    /**
     * 待办：即将过期的正常链接数（默认 24 小时窗口）
     *
     * @return 数量
     */
    long countExpiringSoon();

    /**
     * 个人足迹：当前用户创建的分享数
     *
     * @return 分享数
     */
    long countMyLinks();
}
