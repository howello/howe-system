package com.howe.web.controller.home;

import com.howe.common.constant.CacheConstants;
import com.howe.common.constant.ConfigConstants;
import com.howe.common.core.redis.RedisCache;
import com.howe.common.utils.ConfigUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 首页模块可用性探测服务
 *
 * <p>「模块在不在」在本项目里的真实语义是「该模块的数据表是否已初始化」：
 * 四个业务模块同进程运行，代码永远都在，只有表可能没建。本服务用
 * {@code information_schema.tables} 一次性探测各模块的核心表，并把结果
 * 缓存到 Redis，避免每次首页加载都查系统表。</p>
 *
 * <p>探测失败（数据库异常等）时按「模块可用」处理，交由各卡片自身的
 * 接口失败降级，不让探测异常拖垮整个卡片清单。</p>
 *
 * @author howe
 */
@Slf4j
@Service
public class HomeModuleAvailabilityService {

    /** 模块标识 -> 核心表名；按此顺序探测，返回结果保持稳定顺序 */
    private static final Map<String, String> MODULE_CORE_TABLES = new LinkedHashMap<>();

    static {
        MODULE_CORE_TABLES.put("blog", "blog_article");
        MODULE_CORE_TABLES.put("meal", "meal_order");
        MODULE_CORE_TABLES.put("ai", "ai_call_log");
        MODULE_CORE_TABLES.put("share", "share_link");
    }

    private final JdbcTemplate jdbcTemplate;
    private final RedisCache redisCache;

    public HomeModuleAvailabilityService(JdbcTemplate jdbcTemplate, RedisCache redisCache) {
        this.jdbcTemplate = jdbcTemplate;
        this.redisCache = redisCache;
    }

    /**
     * 获取各模块可用性（模块标识 -> 表是否已初始化）
     *
     * <p>结果整体缓存，TTL 取参数 {@code sys.home.moduleCacheTtl}，默认 300 秒。</p>
     *
     * @return 模块标识到可用性的映射；顺序与 {@link #MODULE_CORE_TABLES} 一致
     */
    @SuppressWarnings("unchecked")
    public Map<String, Boolean> getAvailability() {
        Map<String, Boolean> cached = redisCache.getCacheObject(CacheConstants.HOME_MODULES_KEY);
        if (cached != null) {
            return cached;
        }
        Map<String, Boolean> result = probe();
        int ttl = ConfigUtils.getInt(ConfigConstants.HOME_MODULES_CACHE_TTL, 300);
        redisCache.setCacheObject(CacheConstants.HOME_MODULES_KEY, result, ttl, TimeUnit.SECONDS);
        return result;
    }

    /**
     * 探测指定模块是否可用
     *
     * @param module 模块标识（blog / meal / ai / share）
     * @return 表已初始化返回 true；模块标识未知时按可用处理
     */
    public boolean isAvailable(String module) {
        Boolean available = getAvailability().get(module);
        return available == null || available;
    }

    private Map<String, Boolean> probe() {
        List<String> tables;
        try {
            tables = jdbcTemplate.queryForList(
                    "select table_name from information_schema.tables where table_schema = database()",
                    String.class);
        } catch (Exception e) {
            // 探测失败不阻断首页：全部按可用处理，由各卡片接口自身降级
            log.warn("首页模块可用性探测失败，按全部可用处理：{}", e.getMessage());
            Map<String, Boolean> fallback = new LinkedHashMap<>();
            MODULE_CORE_TABLES.keySet().forEach(m -> fallback.put(m, true));
            return fallback;
        }
        Map<String, Boolean> result = new LinkedHashMap<>();
        MODULE_CORE_TABLES.forEach((module, table) -> result.put(module, containsIgnoreCase(tables, table)));
        return result;
    }

    private boolean containsIgnoreCase(List<String> tables, String table) {
        return tables.stream().anyMatch(t -> t != null && t.equalsIgnoreCase(table));
    }

    /** 供诊断与测试使用：全部受支持的模块标识 */
    public static List<String> supportedModules() {
        return List.copyOf(MODULE_CORE_TABLES.keySet());
    }

    /** 供诊断使用：模块的核心表名 */
    public static String coreTableOf(String module) {
        return MODULE_CORE_TABLES.get(module);
    }
}
