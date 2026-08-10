package com.howe.blog.service.impl;

import com.howe.blog.domain.vo.BlogCategoryCount;
import com.howe.blog.domain.vo.BlogHomeStats;
import com.howe.blog.domain.vo.BlogStats;
import com.howe.blog.domain.vo.BlogTodayStats;
import com.howe.blog.domain.vo.BlogTrendPoint;
import com.howe.blog.mapper.BlogStatsMapper;
import com.howe.blog.service.IBlogStatsService;
import com.howe.common.constant.CacheConstants;
import com.howe.common.constant.ConfigConstants;
import com.howe.common.core.redis.RedisCache;
import com.howe.common.utils.ConfigUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 博客首页统计服务实现
 *
 * <p>统计口径与降级规则见 {@link IBlogStatsService}；结果整体缓存（含降级结果），
 * TTL 取参数 {@code sys.home.statsCacheTtl}，默认 600 秒。</p>
 *
 * @author howe
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BlogStatsServiceImpl implements IBlogStatsService {

    /** 窗口天数：current_date - 29 天 至 current_date，升序固定 30 桶 */
    private static final int TREND_DAYS = 30;

    /** 分类分布保留前 N 名，其余归「其他」 */
    private static final int CATEGORY_TOP_N = 7;

    /** 分类溢出分组名 */
    private static final String OTHER = "其他";

    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    private final BlogStatsMapper blogStatsMapper;
    private final RedisCache redisCache;

    @Override
    public BlogHomeStats getHomeStats() {
        BlogHomeStats cached = redisCache.getCacheObject(CacheConstants.HOME_STATS_KEY);
        if (cached != null) {
            return cached;
        }
        BlogHomeStats result;
        try {
            result = computeStats();
        } catch (BadSqlGrammarException e) {
            // 只认「表不存在」（MySQL ERROR 1146 / SQLState 42S02），其余 SQL 异常照常抛出
            if (e.getSQLException() != null && "42S02".equals(e.getSQLException().getSQLState())) {
                log.warn("博客统计表不存在，首页博客区块降级为不可用：{}", e.getSQLException().getMessage());
                result = new BlogHomeStats(false, null);
            } else {
                throw e;
            }
        }
        int ttl = ConfigUtils.getInt(ConfigConstants.HOME_STATS_CACHE_TTL, 600);
        redisCache.setCacheObject(CacheConstants.HOME_STATS_KEY, result, ttl, TimeUnit.SECONDS);
        return result;
    }

    private BlogHomeStats computeStats() {
        Map<String, Object> total = blogStatsMapper.selectTotalCounts();
        long articleTotal = ((Number) total.get("article_total")).longValue();
        long draftTotal = ((Number) total.get("draft_total")).longValue();
        long talkTotal = ((Number) total.get("talk_total")).longValue();

        LocalDate today = LocalDate.now();
        String start = today.minusDays(TREND_DAYS - 1L).format(DAY_FMT);
        String end = today.format(DAY_FMT);

        // 0=articles 1=drafts 2=talks；LinkedHashMap 保证升序
        Map<String, long[]> buckets = new LinkedHashMap<>();
        for (int i = TREND_DAYS - 1; i >= 0; i--) {
            buckets.put(today.minusDays(i).format(DAY_FMT), new long[3]);
        }
        fillBucket(buckets, blogStatsMapper.selectArticleCountByDay(start, end), 0);
        fillBucket(buckets, blogStatsMapper.selectDraftCountByDay(start, end), 1);
        fillBucket(buckets, blogStatsMapper.selectTalkCountByDay(start, end), 2);

        List<BlogTrendPoint> trend = new ArrayList<>(TREND_DAYS);
        buckets.forEach((date, v) -> trend.add(new BlogTrendPoint(date, v[0], v[1], v[2])));

        // today 由窗口今天桶直接得出（窗口定义恒含今天），不再发额外请求
        long[] last = buckets.get(end);
        BlogTodayStats todayStats = new BlogTodayStats(last[0], last[1], last[2]);

        BlogStats blog = new BlogStats(articleTotal, draftTotal, talkTotal, todayStats,
                trend, aggregateCategories(blogStatsMapper.selectAllArticleCategories()));
        return new BlogHomeStats(true, blog);
    }

    private void fillBucket(Map<String, long[]> buckets, List<Map<String, Object>> rows, int idx) {
        for (Map<String, Object> row : rows) {
            String date = String.valueOf(row.get("d_date"));
            long[] bucket = buckets.get(date);
            if (bucket != null) {
                bucket[idx] = ((Number) row.get("cnt")).longValue();
            }
        }
    }

    private List<BlogCategoryCount> aggregateCategories(List<String> raw) {
        Map<String, Long> counts = new HashMap<>();
        for (String line : raw) {
            if (line == null || line.isBlank()) {
                continue;
            }
            for (String part : line.split(",")) {
                String name = part.trim();
                if (name.isEmpty()) {
                    continue; // 空分类不计入任何分类计数
                }
                counts.merge(name, 1L, Long::sum);
            }
        }
        List<Map.Entry<String, Long>> sorted = new ArrayList<>(counts.entrySet());
        sorted.sort(Collections.reverseOrder(Map.Entry.comparingByValue()));

        List<BlogCategoryCount> result = new ArrayList<>();
        int kept = Math.min(CATEGORY_TOP_N, sorted.size());
        long otherSum = 0;
        for (int i = 0; i < sorted.size(); i++) {
            if (i < kept) {
                result.add(new BlogCategoryCount(sorted.get(i).getKey(), sorted.get(i).getValue()));
            } else {
                otherSum += sorted.get(i).getValue();
            }
        }
        if (otherSum > 0) {
            result.add(new BlogCategoryCount(OTHER, otherSum));
        }
        return result;
    }
}