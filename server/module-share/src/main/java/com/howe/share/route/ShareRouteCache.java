package com.howe.share.route;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.howe.common.constant.ConfigConstants;
import com.howe.common.core.redis.RedisCache;
import com.howe.common.utils.ConfigUtils;
import com.howe.share.constant.ShareConstants;
import com.howe.share.domain.ShareLink;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 分享路由缓存（Redis 只读加速）
 *
 * <p>MySQL 是唯一权威；本缓存只存「字节落在哪」，命中时省一次回库，miss 时由调用方回查并回填。
 * 焚毁 / 过期 / 手动失效 / 删除分享时必须同时删除对应键。</p>
 *
 * @author howe
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ShareRouteCache {

    /** 无过期时间时的默认 TTL（秒） */
    private static final int DEFAULT_TTL = 604800;

    /** JSON 编解码 */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final RedisCache redisCache;

    /**
     * 读取路由（缓存未启用时返回 null）
     *
     * @param code 短码
     * @return 路由；未命中或未启用时返回 null
     */
    public ShareRoute get(String code) {
        if (!enabled() || StrUtil.isBlank(code)) {
            return null;
        }
        try {
            String json = redisCache.getCacheObject(key(code));
            if (StrUtil.isBlank(json)) {
                return null;
            }
            return MAPPER.readValue(json, ShareRoute.class);
        } catch (Exception e) {
            log.warn("读取分享路由缓存失败：code={}", code, e);
            return null;
        }
    }

    /**
     * 写入路由缓存
     *
     * @param link 分享记录（需已就绪、含存储落点）
     */
    public void put(ShareLink link) {
        if (!enabled() || link == null || StrUtil.isBlank(link.getCode())) {
            return;
        }
        try {
            ShareRoute route = new ShareRoute(link.getLinkId(), link.getStorageType(),
                    link.getStorageLocation(), link.getContentType(), link.getFileName(), link.getFileSize());
            redisCache.setCacheObject(key(link.getCode()), MAPPER.writeValueAsString(route), ttl(link), TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("写入分享路由缓存失败：code={}", link.getCode(), e);
        }
    }

    /**
     * 删除路由缓存
     *
     * @param code 短码
     */
    public void evict(String code) {
        if (StrUtil.isBlank(code)) {
            return;
        }
        try {
            redisCache.deleteObject(key(code));
        } catch (Exception e) {
            log.warn("删除分享路由缓存失败：code={}", code, e);
        }
    }

    /**
     * 缓存开关
     */
    private boolean enabled() {
        return ConfigUtils.getBoolean(ConfigConstants.SHARE_ROUTE_CACHE_ENABLED, true);
    }

    /**
     * TTL：有过期时间时对齐它，否则用默认值
     */
    private int ttl(ShareLink link) {
        if (link.getExpireAt() != null) {
            long seconds = (link.getExpireAt().getTime() - System.currentTimeMillis()) / 1000;
            if (seconds <= 0) {
                return 1;
            }
            return (int) Math.min(seconds, Integer.MAX_VALUE);
        }
        return ConfigUtils.getInt(ConfigConstants.SHARE_ROUTE_TTL, DEFAULT_TTL);
    }

    private String key(String code) {
        return ShareConstants.ROUTE_KEY_PREFIX + code;
    }
}
