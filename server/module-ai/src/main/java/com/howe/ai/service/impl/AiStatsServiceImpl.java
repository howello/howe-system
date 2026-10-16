package com.howe.ai.service.impl;

import com.howe.ai.persistence.domain.vo.AiCallLogStats;
import com.howe.ai.persistence.domain.vo.AiChannelHealth;
import com.howe.ai.persistence.domain.vo.AiHomeStats;
import com.howe.ai.persistence.domain.vo.AiImageAssetOverview;
import com.howe.ai.persistence.mapper.AiCallLogMapper;
import com.howe.ai.persistence.mapper.AiChannelMapper;
import com.howe.ai.persistence.mapper.AiImageAssetMapper;
import com.howe.ai.service.IAiStatsService;
import com.howe.common.constant.CacheConstants;
import com.howe.common.constant.ConfigConstants;
import com.howe.common.core.redis.RedisCache;
import com.howe.common.utils.ConfigUtils;
import com.howe.common.utils.DateUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * AI 首页统计服务实现
 *
 * <p>结果整体缓存，TTL 取参数 {@code sys.home.cardCacheTtl}，默认 300 秒。
 * 用量聚合复用既有 {@code AiCallLogMapper#selectStats}，不重复实现口径。</p>
 *
 * @author howe
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiStatsServiceImpl implements IAiStatsService {

    private final AiCallLogMapper aiCallLogMapper;
    private final AiChannelMapper aiChannelMapper;
    private final AiImageAssetMapper aiImageAssetMapper;
    private final RedisCache redisCache;

    @Override
    public AiHomeStats getHomeStats() {
        AiHomeStats cached = redisCache.getCacheObject(CacheConstants.HOME_AI_STATS_KEY);
        if (cached != null) {
            return cached;
        }
        AiHomeStats result = computeHomeStats();
        cache(CacheConstants.HOME_AI_STATS_KEY, result);
        return result;
    }

    @Override
    public AiChannelHealth getChannelHealth() {
        AiChannelHealth cached = redisCache.getCacheObject(CacheConstants.HOME_AI_CHANNEL_KEY);
        if (cached != null) {
            return cached;
        }
        AiChannelHealth result = computeChannelHealth();
        cache(CacheConstants.HOME_AI_CHANNEL_KEY, result);
        return result;
    }

    @Override
    public AiImageAssetOverview getImageAssetOverview() {
        AiImageAssetOverview cached = redisCache.getCacheObject(CacheConstants.HOME_AI_IMAGE_KEY);
        if (cached != null) {
            return cached;
        }
        AiImageAssetOverview result = computeImageAssetOverview();
        cache(CacheConstants.HOME_AI_IMAGE_KEY, result);
        return result;
    }

    private AiHomeStats computeHomeStats() {
        Date begin = startOfToday();
        Date end = endOfToday();
        List<AiCallLogStats> stats = aiCallLogMapper.selectStats("scene", begin, end, null);

        long total = 0, success = 0, tokens = 0;
        List<AiHomeStats.SceneUsage> scenes = new ArrayList<>();
        for (AiCallLogStats s : stats) {
            long count = s.getTotalCount() == null ? 0L : s.getTotalCount();
            total += count;
            success += s.getSuccessCount() == null ? 0L : s.getSuccessCount();
            tokens += (s.getInputTokens() == null ? 0L : s.getInputTokens())
                    + (s.getOutputTokens() == null ? 0L : s.getOutputTokens());
            scenes.add(new AiHomeStats.SceneUsage(
                    s.getGroupKey() == null ? "" : s.getGroupKey(), count));
        }
        double rate = total == 0 ? 0D : Math.round(success * 1000D / total) / 10D;
        return new AiHomeStats(total, tokens, rate, scenes);
    }

    private AiChannelHealth computeChannelHealth() {
        List<AiChannelHealth.ChannelStatus> channels = new ArrayList<>();
        for (Map<String, Object> row : aiChannelMapper.selectHealthOverview()) {
            Object name = row.get("name");
            Object health = row.get("health_status");
            channels.add(new AiChannelHealth.ChannelStatus(
                    name == null ? "" : String.valueOf(name),
                    health == null ? "UNKNOWN" : String.valueOf(health)));
        }
        long down = aiChannelMapper.selectDownCount();
        long disabled = aiChannelMapper.selectDisabledCount();
        Date latest = aiChannelMapper.selectLatestCheckTime();
        return new AiChannelHealth(channels, down, disabled, formatTime(latest));
    }

    private AiImageAssetOverview computeImageAssetOverview() {
        Map<String, Object> overview = aiImageAssetMapper.selectOverview();
        long count = 0L, bytes = 0L;
        if (overview != null) {
            count = overview.get("total_count") instanceof Number n ? n.longValue() : 0L;
            bytes = overview.get("total_bytes") instanceof Number n ? n.longValue() : 0L;
        }
        return new AiImageAssetOverview(count, bytes, formatTime(aiImageAssetMapper.selectLatestCreateTime()));
    }

    private Date startOfToday() {
        return Date.from(LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    private Date endOfToday() {
        return Date.from(LocalDateTime.now().withHour(23).withMinute(59).withSecond(59)
                .atZone(ZoneId.systemDefault()).toInstant());
    }

    private String formatTime(Date value) {
        if (value == null) {
            return null;
        }
        return DateUtils.parseDateToStr(DateUtils.YYYY_MM_DD_HH_MM_SS, value).substring(0, 16);
    }

    private void cache(String key, Object value) {
        int ttl = ConfigUtils.getInt(ConfigConstants.HOME_CARD_CACHE_TTL, 300);
        redisCache.setCacheObject(key, value, ttl, TimeUnit.SECONDS);
    }
}
