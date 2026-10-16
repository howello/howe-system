package com.howe.share.service.impl;

import com.howe.common.constant.CacheConstants;
import com.howe.common.constant.ConfigConstants;
import com.howe.common.core.redis.RedisCache;
import com.howe.common.utils.ConfigUtils;
import com.howe.common.utils.DateUtils;
import com.howe.common.utils.SecurityUtils;
import com.howe.share.domain.vo.ShareCacheHealth;
import com.howe.share.domain.vo.ShareHomeStats;
import com.howe.share.mapper.ShareStatsMapper;
import com.howe.share.service.IShareStatsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 分享首页统计服务实现
 *
 * <p>结果整体缓存，TTL 取参数 {@code sys.home.cardCacheTtl}，默认 300 秒。</p>
 *
 * @author howe
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShareStatsServiceImpl implements IShareStatsService {

    /** 视为「访问」的动作 */
    private static final List<String> VIEW_ACTIONS = List.of("detail", "verify", "open", "file");

    /** 视为「下载」的动作 */
    private static final List<String> DOWNLOAD_ACTIONS = List.of("download");

    /** 最近创建链接保留条数 */
    private static final int RECENT_LINK_LIMIT = 3;

    /** 「即将过期」窗口：24 小时 */
    private static final int EXPIRING_SOON_HOURS = 24;

    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    private final ShareStatsMapper shareStatsMapper;
    private final RedisCache redisCache;

    @Override
    public ShareHomeStats getHomeStats() {
        ShareHomeStats cached = redisCache.getCacheObject(CacheConstants.HOME_SHARE_STATS_KEY);
        if (cached != null) {
            return cached;
        }
        ShareHomeStats result = computeHomeStats();
        cache(CacheConstants.HOME_SHARE_STATS_KEY, result);
        return result;
    }

    @Override
    public ShareCacheHealth getCacheHealth() {
        ShareCacheHealth cached = redisCache.getCacheObject(CacheConstants.HOME_SHARE_CACHE_KEY);
        if (cached != null) {
            return cached;
        }
        ShareCacheHealth result = computeCacheHealth();
        cache(CacheConstants.HOME_SHARE_CACHE_KEY, result);
        return result;
    }

    @Override
    public long countExpiringSoon() {
        Date before = new Date(System.currentTimeMillis() + EXPIRING_SOON_HOURS * 3600_000L);
        return shareStatsMapper.selectExpiringSoonCount(before);
    }

    @Override
    public long countMyLinks() {
        return shareStatsMapper.selectMyLinkCount(SecurityUtils.getUsername());
    }

    private ShareHomeStats computeHomeStats() {
        long total = shareStatsMapper.selectLinkTotal();
        String today = LocalDate.now().format(DAY_FMT);
        long views = shareStatsMapper.selectAccessCountByActions(VIEW_ACTIONS, today, today);
        long downloads = shareStatsMapper.selectAccessCountByActions(DOWNLOAD_ACTIONS, today, today);

        List<ShareHomeStats.RecentLink> recent = new ArrayList<>();
        for (Map<String, Object> row : shareStatsMapper.selectRecentLinks(RECENT_LINK_LIMIT)) {
            Object name = row.get("file_name");
            Object time = row.get("create_time");
            recent.add(new ShareHomeStats.RecentLink(
                    name == null ? "" : String.valueOf(name),
                    formatTime(time)));
        }
        return new ShareHomeStats(total, views, downloads, recent);
    }

    private ShareCacheHealth computeCacheHealth() {
        long ready = 0, pending = 0, failed = 0;
        for (Map<String, Object> row : shareStatsMapper.selectLinkCountByCacheStatus()) {
            long cnt = row.get("cnt") instanceof Number n ? n.longValue() : 0L;
            String status = row.get("cache_status") == null ? "" : String.valueOf(row.get("cache_status"));
            switch (status) {
                case "2" -> ready = cnt;
                case "0" -> pending = cnt;
                case "3" -> failed = cnt;
                default -> {
                    // 缓存中（1）不计入健康视图
                }
            }
        }
        return new ShareCacheHealth(ready, pending, failed);
    }

    private String formatTime(Object value) {
        if (value instanceof Date d) {
            return DateUtils.parseDateToStr(DateUtils.YYYY_MM_DD_HH_MM_SS, d).substring(0, 16);
        }
        return value == null ? "" : String.valueOf(value);
    }

    private void cache(String key, Object value) {
        int ttl = ConfigUtils.getInt(ConfigConstants.HOME_CARD_CACHE_TTL, 300);
        redisCache.setCacheObject(key, value, ttl, TimeUnit.SECONDS);
    }
}
