package com.howe.common.core.redis;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.BoundSetOperations;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/**
 * spring redis 工具类
 *
 * @author howe
 **/
@SuppressWarnings(value = { "unchecked", "rawtypes" })
@Component
public class RedisCache
{
    @Autowired
    @Qualifier("redisTemplate")
    public RedisTemplate redisTemplate;

    @Autowired(required = false)
    @Qualifier("notificationRedisTemplate")
    private RedisTemplate<String, String> notificationRedisTemplate;

    @Autowired(required = false)
    @Qualifier("notifyPollScript")
    private DefaultRedisScript<List> notifyPollScript;

    @Autowired(required = false)
    @Qualifier("notifyPushScript")
    private DefaultRedisScript<Long> notifyPushScript;

    /**
     * 保留全局通知 ID 并原子写入队列及过期时间。
     *
     * @param queueKey 通知队列键
     * @param idKey 全局 ID 保留键
     * @param member 通知 JSON
     * @param score 创建时间戳
     * @param timeout 过期时长
     * @param unit 过期单位
     * @return ID 可用并成功写入时返回 true
     */
    public boolean addNotificationIfAbsent(final String queueKey, final String idKey, final String member,
            final double score, final long timeout, final TimeUnit unit)
    {
        if (notificationRedisTemplate == null || notifyPushScript == null)
        {
            throw new IllegalStateException("通知 RedisTemplate 未配置");
        }
        Long result = (Long) notificationRedisTemplate.execute(notifyPushScript,
                Collections.singletonList(queueKey), member, Double.toString(score),
                Long.toString(unit.toSeconds(timeout)), idKey);
        return result != null && result == 1L;
    }

    /**
     * 原子读取并清空通知队列。
     *
     * @param key 通知队列键
     * @return 通知 JSON 字符串集合
     */
    public List<String> pollNotifications(final String key)
    {
        if (notificationRedisTemplate == null || notifyPollScript == null)
        {
            throw new IllegalStateException("通知 Redis 执行环境未配置");
        }
        List<String> result = notificationRedisTemplate.execute(notifyPollScript,
                Collections.singletonList(key));
        return result == null ? Collections.emptyList() : result;
    }

    /**
     * 原子写入字符串缓存：先检查守卫键不存在，再写入并设置过期时间。
     *
     * <p>检查与写入由同一段 Lua 脚本完成，不存在「查完再写」的时间窗口。
     * 用于业务侧「结束标记写入后不得再写回缓存」这类互斥场景。
     * 与通知模板共用纯字符串序列化，避免对象序列化器给脚本参数加引号。</p>
     *
     * @param guardKey 守卫键，存在时拒绝写入
     * @param key 目标缓存键
     * @param value 字符串值
     * @param timeout 过期时长
     * @param unit 时间单位
     * @return 实际写入返回 true；守卫键存在或执行失败返回 false
     */
    public boolean setStringIfGuardAbsent(final String guardKey, final String key, final String value,
            final long timeout, final TimeUnit unit)
    {
        return setStringIfGuardAbsent(guardKey, key, value, null, timeout, unit);
    }

    /** 守卫键不存在且缓存仍是指定占位值时才写入；期望值为空时跳过占位校验。 */
    public boolean setStringIfGuardAbsent(final String guardKey, final String key, final String value,
            final String expectedValue, final long timeout, final TimeUnit unit) {
        if (notificationRedisTemplate == null)
        {
            throw new IllegalStateException("通知 RedisTemplate 未配置");
        }
        Long result = (Long) notificationRedisTemplate.execute(SET_IF_GUARD_ABSENT_SCRIPT,
                Arrays.asList(guardKey, key), value, Long.toString(unit.toSeconds(timeout)),
                expectedValue == null ? "" : expectedValue);
        return result != null && result == 1L;
    }

    /**
     * 原子写入守卫键并删除目标缓存。
     *
     * <p>用于业务侧「结束/删除后清理缓存」：守卫键与删除在同一脚本内完成，
     * 后续通过 {@link #setStringIfGuardAbsent(String, String, String, long, TimeUnit)}
     * 写入的线程会被立即拒绝。</p>
     *
     * @param guardKey 守卫键
     * @param key 目标缓存键
     * @param timeout 守卫键过期时长
     * @param unit 时间单位
     * @return 脚本执行成功返回 true
     */
    public boolean closeByGuard(final String guardKey, final String key, final long timeout,
            final TimeUnit unit)
    {
        if (notificationRedisTemplate == null)
        {
            throw new IllegalStateException("通知 RedisTemplate 未配置");
        }
        Long result = (Long) notificationRedisTemplate.execute(CLOSE_BY_GUARD_SCRIPT,
                Arrays.asList(guardKey, key), Long.toString(unit.toSeconds(timeout)));
        return result != null && result == 1L;
    }

    /** 守卫键不存在时原子写入字符串缓存。 */
    private static final DefaultRedisScript<Long> SET_IF_GUARD_ABSENT_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('EXISTS', KEYS[1]) == 1 then return 0 end\n"
                    + "if ARGV[3] ~= '' and redis.call('GET', KEYS[2]) ~= ARGV[3] then return 0 end\n"
                    + "redis.call('SET', KEYS[2], ARGV[1], 'EX', ARGV[2])\n"
                    + "return 1",
            Long.class);

    /** 原子写入守卫键并删除目标缓存。 */
    private static final DefaultRedisScript<Long> CLOSE_BY_GUARD_SCRIPT = new DefaultRedisScript<>(
            "redis.call('SET', KEYS[1], '1', 'EX', ARGV[1])\n"
                    + "redis.call('DEL', KEYS[2])\n"
                    + "return 1",
            Long.class);

    /**
     * 缓存基本的对象，Integer、String、实体类等
     *
     * @param key 缓存的键值
     * @param value 缓存的值
     */
    public <T> void setCacheObject(final String key, final T value)
    {
        redisTemplate.opsForValue().set(key, value);
    }

    /**
     * 缓存基本的对象，Integer、String、实体类等
     *
     * @param key 缓存的键值
     * @param value 缓存的值
     * @param timeout 时间
     * @param timeUnit 时间颗粒度
     */
    public <T> void setCacheObject(final String key, final T value, final Integer timeout, final TimeUnit timeUnit)
    {
        redisTemplate.opsForValue().set(key, value, timeout, timeUnit);
    }

    /**
     * 设置有效时间
     *
     * @param key Redis键
     * @param timeout 超时时间
     * @return true=设置成功；false=设置失败
     */
    public boolean expire(final String key, final long timeout)
    {
        return expire(key, timeout, TimeUnit.SECONDS);
    }

    /**
     * 设置有效时间
     *
     * @param key Redis键
     * @param timeout 超时时间
     * @param unit 时间单位
     * @return true=设置成功；false=设置失败
     */
    public boolean expire(final String key, final long timeout, final TimeUnit unit)
    {
        return redisTemplate.expire(key, timeout, unit);
    }

    /**
     * 获取有效时间
     *
     * @param key Redis键
     * @return 有效时间
     */
    public long getExpire(final String key)
    {
        return redisTemplate.getExpire(key);
    }

    /**
     * 判断 key是否存在
     *
     * @param key 键
     * @return true 存在 false不存在
     */
    public Boolean hasKey(String key)
    {
        return redisTemplate.hasKey(key);
    }

    /**
     * 获得缓存的基本对象。
     *
     * @param key 缓存键值
     * @return 缓存键值对应的数据
     */
    public <T> T getCacheObject(final String key)
    {
        ValueOperations<String, T> operation = redisTemplate.opsForValue();
        return operation.get(key);
    }

    /**
     * 删除单个对象
     *
     * @param key
     */
    public boolean deleteObject(final String key)
    {
        return redisTemplate.delete(key);
    }

    /**
     * 删除集合对象
     *
     * @param collection 多个对象
     * @return
     */
    public boolean deleteObject(final Collection collection)
    {
        return redisTemplate.delete(collection) > 0;
    }

    /**
     * 缓存List数据
     *
     * @param key 缓存的键值
     * @param dataList 待缓存的List数据
     * @return 缓存的对象
     */
    public <T> long setCacheList(final String key, final List<T> dataList)
    {
        Long count = redisTemplate.opsForList().rightPushAll(key, dataList);
        return count == null ? 0 : count;
    }

    /**
     * 获得缓存的list对象
     *
     * @param key 缓存的键值
     * @return 缓存键值对应的数据
     */
    public <T> List<T> getCacheList(final String key)
    {
        return redisTemplate.opsForList().range(key, 0, -1);
    }

    /**
     * 缓存Set
     *
     * @param key 缓存键值
     * @param dataSet 缓存的数据
     * @return 缓存数据的对象
     */
    public <T> BoundSetOperations<String, T> setCacheSet(final String key, final Set<T> dataSet)
    {
        BoundSetOperations<String, T> setOperation = redisTemplate.boundSetOps(key);
        Iterator<T> it = dataSet.iterator();
        while (it.hasNext())
        {
            setOperation.add(it.next());
        }
        return setOperation;
    }

    /**
     * 获得缓存的set
     *
     * @param key
     * @return
     */
    public <T> Set<T> getCacheSet(final String key)
    {
        return redisTemplate.opsForSet().members(key);
    }

    /**
     * 缓存Map
     *
     * @param key
     * @param dataMap
     */
    public <T> void setCacheMap(final String key, final Map<String, T> dataMap)
    {
        if (dataMap != null) {
            redisTemplate.opsForHash().putAll(key, dataMap);
        }
    }

    /**
     * 获得缓存的Map
     *
     * @param key
     * @return
     */
    public <T> Map<String, T> getCacheMap(final String key)
    {
        return redisTemplate.opsForHash().entries(key);
    }

    /**
     * 往Hash中存入数据
     *
     * @param key Redis键
     * @param hKey Hash键
     * @param value 值
     */
    public <T> void setCacheMapValue(final String key, final String hKey, final T value)
    {
        redisTemplate.opsForHash().put(key, hKey, value);
    }

    /**
     * 获取Hash中的数据
     *
     * @param key Redis键
     * @param hKey Hash键
     * @return Hash中的对象
     */
    public <T> T getCacheMapValue(final String key, final String hKey)
    {
        HashOperations<String, String, T> opsForHash = redisTemplate.opsForHash();
        return opsForHash.get(key, hKey);
    }

    /**
     * 获取多个Hash中的数据
     *
     * @param key Redis键
     * @param hKeys Hash键集合
     * @return Hash对象集合
     */
    public <T> List<T> getMultiCacheMapValue(final String key, final Collection<Object> hKeys)
    {
        return redisTemplate.opsForHash().multiGet(key, hKeys);
    }

    /**
     * 删除Hash中的某条数据
     *
     * @param key Redis键
     * @param hKey Hash键
     * @return 是否成功
     */
    public boolean deleteCacheMapValue(final String key, final String hKey)
    {
        return redisTemplate.opsForHash().delete(key, hKey) > 0;
    }

    /**
     * 获得缓存的基本对象列表
     *
     * @param pattern 字符串前缀
     * @return 对象列表
     */
    public Collection<String> keys(final String pattern)
    {
        return redisTemplate.keys(pattern);
    }

    /**
     * 加入有序集合
     *
     * @param key    集合键
     * @param member 成员
     * @param score  分值（补偿队列里用作下次重试时间戳）
     * @return 是否成功
     */
    public boolean zAdd(final String key, final String member, final double score)
    {
        Boolean added = redisTemplate.opsForZSet().add(key, member, score);
        return Boolean.TRUE.equals(added);
    }

    /**
     * 按分值区间升序取成员
     *
     * @param key   集合键
     * @param min   最小分值（含）
     * @param max   最大分值（含）
     * @param count 最多取多少个
     * @return 成员集合；无匹配时返回空集合
     */
    public Set<String> zRangeByScore(final String key, final double min, final double max, final long count)
    {
        Set<Object> members = redisTemplate.opsForZSet().rangeByScore(key, min, max, 0, count);
        if (members == null || members.isEmpty())
        {
            return Collections.emptySet();
        }
        Set<String> result = new LinkedHashSet<>(members.size());
        for (Object member : members)
        {
            result.add(String.valueOf(member));
        }
        return result;
    }

    /**
     * 从有序集合移除成员
     *
     * @param key     集合键
     * @param members 成员
     * @return 是否成功
     */
    public boolean zRemove(final String key, final String... members)
    {
        Long removed = redisTemplate.opsForZSet().remove(key, (Object[]) members);
        return removed != null && removed > 0;
    }
}
