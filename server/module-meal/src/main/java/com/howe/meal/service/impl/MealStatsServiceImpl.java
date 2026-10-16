package com.howe.meal.service.impl;

import com.howe.common.constant.CacheConstants;
import com.howe.common.constant.ConfigConstants;
import com.howe.common.core.redis.RedisCache;
import com.howe.common.utils.ConfigUtils;
import com.howe.common.utils.DateUtils;
import com.howe.common.utils.SecurityUtils;
import com.howe.meal.domain.MealAppVersion;
import com.howe.meal.domain.vo.MealAppVersionOverview;
import com.howe.meal.domain.vo.MealDishOverview;
import com.howe.meal.domain.vo.MealHomeStats;
import com.howe.meal.domain.vo.MealHotDishes;
import com.howe.meal.mapper.MealAppVersionMapper;
import com.howe.meal.mapper.MealStatsMapper;
import com.howe.meal.service.IMealStatsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 点餐首页统计服务实现
 *
 * <p>数据范围与点餐列表接口一致：平台管理员看全量，其余用户只看本家庭。
 * 结果整体缓存（含零值），TTL 取参数 {@code sys.home.cardCacheTtl}，默认 300 秒。</p>
 *
 * @author howe
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MealStatsServiceImpl implements IMealStatsService {

    /** 趋势窗口天数 */
    private static final int TREND_DAYS = 7;

    /** 热门菜品窗口天数 */
    private static final int HOT_DISH_DAYS = 30;

    /** 热门菜品保留条数 */
    private static final int HOT_DISH_TOP_N = 5;

    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    private final MealStatsMapper mealStatsMapper;
    private final MealAppVersionMapper mealAppVersionMapper;
    private final RedisCache redisCache;

    @Override
    public MealHomeStats getHomeStats() {
        String key = CacheConstants.HOME_MEAL_STATS_KEY + deptScopeSuffix();
        MealHomeStats cached = redisCache.getCacheObject(key);
        if (cached != null) {
            return cached;
        }
        MealHomeStats result = computeHomeStats();
        cache(key, result);
        return result;
    }

    @Override
    public MealDishOverview getDishOverview() {
        String key = CacheConstants.HOME_MEAL_DISH_KEY + deptScopeSuffix();
        MealDishOverview cached = redisCache.getCacheObject(key);
        if (cached != null) {
            return cached;
        }
        MealDishOverview result = computeDishOverview();
        cache(key, result);
        return result;
    }

    @Override
    public MealAppVersionOverview getAppVersionOverview() {
        MealAppVersionOverview cached = redisCache.getCacheObject(CacheConstants.HOME_MEAL_VERSION_KEY);
        if (cached != null) {
            return cached;
        }
        MealAppVersionOverview result = computeAppVersionOverview();
        cache(CacheConstants.HOME_MEAL_VERSION_KEY, result);
        return result;
    }

    @Override
    public MealHotDishes getHotDishes() {
        String key = CacheConstants.HOME_MEAL_HOT_DISH_KEY + deptScopeSuffix();
        MealHotDishes cached = redisCache.getCacheObject(key);
        if (cached != null) {
            return cached;
        }
        MealHotDishes result = computeHotDishes();
        cache(key, result);
        return result;
    }

    @Override
    public long countPendingProposals() {
        return mealStatsMapper.selectPendingProposalCount(currentDeptScope());
    }

    @Override
    public long countPendingOrders() {
        return mealStatsMapper.selectPendingOrderCount(currentDeptScope());
    }

    @Override
    public long countMyOrders() {
        return mealStatsMapper.selectMyOrderCount(SecurityUtils.getUserId());
    }

    @Override
    public long countMyProposals() {
        return mealStatsMapper.selectMyProposalCount(SecurityUtils.getUserId());
    }

    private MealHomeStats computeHomeStats() {
        Long deptId = currentDeptScope();
        LocalDate today = LocalDate.now();
        String start = today.minusDays(TREND_DAYS - 1L).format(DAY_FMT);
        String end = today.format(DAY_FMT);

        long pending = 0, cooking = 0, done = 0;
        long todayTotal = 0;
        for (Map<String, Object> row : mealStatsMapper.selectOrderCountByStatus(deptId, end, end)) {
            long cnt = number(row.get("cnt"));
            // 今日订单数 = 当天创建的全部订单（含已取消），与近 7 天趋势口径一致
            todayTotal += cnt;
            String status = row.get("status") == null ? "" : String.valueOf(row.get("status"));
            switch (status) {
                case "0" -> pending = cnt;
                case "1" -> cooking = cnt;
                case "2" -> done = cnt;
                default -> {
                    // 取消等其它状态不计入状态分布，但已计入今日订单总数
                }
            }
        }
        long todayOrders = todayTotal;

        // 近 7 天：先建满桶保证缺日补 0、升序、固定 7 元素
        Map<String, Long> buckets = new LinkedHashMap<>();
        for (int i = TREND_DAYS - 1; i >= 0; i--) {
            buckets.put(today.minusDays(i).format(DAY_FMT), 0L);
        }
        for (Map<String, Object> row : mealStatsMapper.selectOrderCountByDay(deptId, start, end)) {
            String date = row.get("d_date") == null ? null : String.valueOf(row.get("d_date"));
            if (date != null && buckets.containsKey(date)) {
                buckets.put(date, number(row.get("cnt")));
            }
        }
        List<MealHomeStats.MealOrderTrendPoint> trend = new ArrayList<>(TREND_DAYS);
        buckets.forEach((date, cnt) -> trend.add(new MealHomeStats.MealOrderTrendPoint(date, cnt)));

        Double avg = mealStatsMapper.selectAvgReviewScore(deptId);
        double avgScore = avg == null ? 0D : Math.round(avg * 10D) / 10D;

        return new MealHomeStats(todayOrders, pending, cooking, done, avgScore, trend);
    }

    private MealDishOverview computeDishOverview() {
        Long deptId = currentDeptScope();
        long onShelf = 0, offShelf = 0;
        for (Map<String, Object> row : mealStatsMapper.selectDishCountByStatus(deptId)) {
            long cnt = number(row.get("cnt"));
            String status = row.get("status") == null ? "" : String.valueOf(row.get("status"));
            if ("0".equals(status)) {
                onShelf = cnt;
            } else if ("1".equals(status)) {
                offShelf = cnt;
            }
        }
        Map<String, Object> ownership = mealStatsMapper.selectDishCountByOwnership(deptId);
        long publicCount = ownership == null ? 0L : number(ownership.get("public_cnt"));
        long familyCount = ownership == null ? 0L : number(ownership.get("family_cnt"));
        long categoryCount = mealStatsMapper.selectCategoryCount(deptId);
        return new MealDishOverview(onShelf, offShelf, categoryCount, publicCount, familyCount);
    }

    private MealAppVersionOverview computeAppVersionOverview() {
        MealAppVersion latest = mealAppVersionMapper.selectLatestPublished();
        long total = mealStatsMapper.selectAppVersionCount();
        if (latest == null) {
            return new MealAppVersionOverview(null, null, false, total);
        }
        String publishTime = latest.getCreateTime() == null
                ? null
                : DateUtils.parseDateToStr(DateUtils.YYYY_MM_DD_HH_MM_SS, latest.getCreateTime()).substring(0, 16);
        boolean force = "1".equals(latest.getForceUpdate());
        return new MealAppVersionOverview(latest.getVersionName(), publishTime, force, total);
    }

    private MealHotDishes computeHotDishes() {
        LocalDate today = LocalDate.now();
        String startDay = today.minusDays(HOT_DISH_DAYS - 1L).format(DAY_FMT);
        String endDay = today.format(DAY_FMT);
        List<MealHotDishes.HotDish> dishes = new ArrayList<>();
        for (Map<String, Object> row : mealStatsMapper.selectHotDishes(
                currentDeptScope(), startDay, endDay, HOT_DISH_TOP_N)) {
            Object name = row.get("dish_name");
            dishes.add(new MealHotDishes.HotDish(name == null ? "" : String.valueOf(name), number(row.get("cnt"))));
        }
        return new MealHotDishes(dishes);
    }

    /**
     * 当前用户的数据范围：平台管理员返回 null（不限），其余返回本家庭ID
     *
     * @return 家庭ID或 null
     */
    private Long currentDeptScope() {
        if (SecurityUtils.isAdmin()) {
            return null;
        }
        return SecurityUtils.getDeptId();
    }

    /** 缓存键按数据范围分片，避免平台管理员与家庭成员互相读到对方的统计 */
    private String deptScopeSuffix() {
        Long deptId = currentDeptScope();
        return deptId == null ? ":all" : ":" + deptId;
    }

    private void cache(String key, Object value) {
        int ttl = ConfigUtils.getInt(ConfigConstants.HOME_CARD_CACHE_TTL, 300);
        redisCache.setCacheObject(key, value, ttl, TimeUnit.SECONDS);
    }

    private long number(Object value) {
        return value instanceof Number n ? n.longValue() : 0L;
    }
}
