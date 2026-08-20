package com.howe.automation.session;

import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Service;
import com.howe.common.core.redis.RedisCache;
import com.howe.common.utils.StringUtils;
import lombok.RequiredArgsConstructor;

/**
 * Playwright 登录态 Redis 存储。
 *
 * <p>当前版本按需求明文保存 storageState；Redis 本身必须配置 ACL、密码、TLS
 * 和内部网络隔离。TTL 固定 30 天，成功使用后续期。</p>
 */
@Service
@RequiredArgsConstructor
public class BrowserSessionStore
{
    private static final String KEY_PREFIX = "automation:session:";
    private static final int TTL_DAYS = 30;

    private final RedisCache redisCache;

    /**
     * 读取任务会话。
     *
     * @param taskKey 任务稳定标识
     * @param credentialAlias 凭据别名
     * @return storageState JSON，未找到时返回 null
     */
    public String get(String taskKey, String credentialAlias)
    {
        Object value = redisCache.getCacheObject(key(taskKey, credentialAlias));
        return value == null ? null : value.toString();
    }

    /**
     * 保存并设置 30 天 TTL。
     *
     * @param taskKey 任务稳定标识
     * @param credentialAlias 凭据别名
     * @param storageState Playwright storageState JSON
     */
    public void save(String taskKey, String credentialAlias, String storageState)
    {
        redisCache.setCacheObject(key(taskKey, credentialAlias), storageState, TTL_DAYS, TimeUnit.DAYS);
    }

    /**
     * 删除失效会话。
     *
     * @param taskKey 任务稳定标识
     * @param credentialAlias 凭据别名
     */
    public void delete(String taskKey, String credentialAlias)
    {
        redisCache.deleteObject(key(taskKey, credentialAlias));
    }

    /**
     * 成功使用后刷新 TTL。
     *
     * @param taskKey 任务稳定标识
     * @param credentialAlias 凭据别名
     */
    public void refresh(String taskKey, String credentialAlias)
    {
        redisCache.expire(key(taskKey, credentialAlias), TTL_DAYS, TimeUnit.DAYS);
    }

    /**
     * 生成稳定 Redis key，禁止把密码或 storageState 拼入 key。
     */
    private String key(String taskKey, String credentialAlias)
    {
        String safeTask = normalize(taskKey, "task");
        String safeAlias = normalize(credentialAlias, "default");
        return KEY_PREFIX + safeTask + ":" + safeAlias;
    }

    private String normalize(String value, String fallback)
    {
        if (StringUtils.isEmpty(value))
        {
            return fallback;
        }
        return value.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
