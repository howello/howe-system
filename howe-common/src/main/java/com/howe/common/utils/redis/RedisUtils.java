package com.howe.common.utils.redis;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSONObject;
import com.howe.common.enums.redis.RedisKeyPrefixEnum;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * <p>@Author lu
 * <p>@Date 2022/11/8 16:37 星期二
 * <p>@Version 1.0
 * <p>@Description
 */
@Component
public class RedisUtils {
    public static final String KEY_SEPARATOR = ":";

    private static final long ERROR_CODE = -1;

    private final RedisTemplate<String, Object> redisTemplate;

    @Autowired
    public RedisUtils(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    // =============================common============================

    private String buildKey(RedisKeyPrefixEnum prefix, String key) {
        return prefix.getPrefix() + KEY_SEPARATOR + key;
    }


    public Long incr(RedisKeyPrefixEnum prefix, String key, long delta) {
        key = buildKey(prefix, key);
        return redisTemplate.opsForValue().increment(key, delta);
    }


    public Long decr(RedisKeyPrefixEnum prefix, String key, long delta) {
        key = buildKey(prefix, key);
        return redisTemplate.opsForValue().decrement(key, delta);
    }


    public Boolean expire(RedisKeyPrefixEnum prefix, String key, long time, TimeUnit timeUnit) {
        key = buildKey(prefix, key);
        try {
            if (time > 0) {
                redisTemplate.expire(key, time, timeUnit);
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }


    public Long getExpire(RedisKeyPrefixEnum prefix, String key, TimeUnit timeUnit) {
        key = buildKey(prefix, key);
        return redisTemplate.getExpire(key, timeUnit);
    }


    public Boolean hasKey(RedisKeyPrefixEnum prefix, String key) {
        key = buildKey(prefix, key);
        try {
            return redisTemplate.hasKey(key);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 获取指定keys
     *
     * @param redisKeyPrefix
     * @return
     */
    public Set<String> keys(RedisKeyPrefixEnum redisKeyPrefix) {
        Set<String> keys = redisTemplate.keys(StrUtil.format("*{}*", redisKeyPrefix.getPrefix()));
        return keys;
    }


    public void del(RedisKeyPrefixEnum prefix, String... key) {
        if (ArrayUtils.isNotEmpty(key)) {
            List<String> keys = Arrays.stream(key)
                    .map(k -> buildKey(prefix, k))
                    .collect(Collectors.toList());
            redisTemplate.delete(keys);
        }
    }


    /**
     * 删除指定前缀的所有key
     *
     * @param redisKeyPrefix
     */
    public void deleteKeys(RedisKeyPrefixEnum redisKeyPrefix) {
        Set<String> keys = keys(redisKeyPrefix);
        redisTemplate.delete(keys);
    }


    // ============================String=============================


    public String get(RedisKeyPrefixEnum prefix, String key) {
        key = buildKey(prefix, key);
        Object o = redisTemplate.opsForValue().get(key);
        if (o instanceof String) {
            return o.toString();
        }
        return null;
    }


    public Boolean set(RedisKeyPrefixEnum prefix, String key, String value) {
        key = buildKey(prefix, key);
        try {
            redisTemplate.opsForValue().set(key, value);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 字符串放入，带时间
     *
     * @param prefix  前缀
     * @param key     键
     * @param value   值
     * @param timeout
     * @return
     */

    public Boolean set(RedisKeyPrefixEnum prefix, String key, String value, long timeout) {
        return set(prefix, key, value, timeout, TimeUnit.MILLISECONDS);
    }


    public Boolean set(RedisKeyPrefixEnum prefix, String key, String value, long timeout, TimeUnit timeUnit) {
        key = buildKey(prefix, key);
        try {
            if (timeout > 0) {
                redisTemplate.opsForValue().set(key, value, timeout, timeUnit);
            } else {
                set(prefix, key, value);
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    // ============================Object=============================


    public Object getObject(RedisKeyPrefixEnum prefix, String key) {
        key = buildKey(prefix, key);
        return redisTemplate.opsForValue().get(key);
    }


    public <T> T getObject(RedisKeyPrefixEnum prefix, String key, Class<T> clazz) {
        key = buildKey(prefix, key);
        Object o = redisTemplate.opsForValue().get(key);
        return o == null ? null : ((JSONObject) o).toJavaObject(clazz);
    }


    public Boolean set(RedisKeyPrefixEnum prefix, String key, Object value) {
        key = buildKey(prefix, key);
        try {
            redisTemplate.opsForValue().set(key, value);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 对象放入并设置时间
     *
     * @param prefix 前缀
     * @param key    键
     * @param value  值
     * @param time   时间(秒) time要大于0 如果time小于等于0 将设置无限期
     * @return true成功 false 失败
     */

    public Boolean set(RedisKeyPrefixEnum prefix, String key, Object value, long time) {
        return set(prefix, key, value, time, TimeUnit.MILLISECONDS);
    }


    public Boolean set(RedisKeyPrefixEnum prefix, String key, Object value, long time, TimeUnit timeUnit) {
        key = buildKey(prefix, key);
        try {
            if (time > 0) {
                redisTemplate.opsForValue().set(key, value, time, timeUnit);
            } else {
                set(prefix, key, value);
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }


    // ================================Map=================================


    public Object hget(RedisKeyPrefixEnum prefix, String key, String item) {
        key = buildKey(prefix, key);
        return redisTemplate.opsForHash().get(key, item);
    }


    public Map<Object, Object> hmget(RedisKeyPrefixEnum prefix, String key) {
        key = buildKey(prefix, key);
        return redisTemplate.opsForHash().entries(key);
    }


    public Boolean hmset(RedisKeyPrefixEnum prefix, String key, Map<String, Object> map) {
        key = buildKey(prefix, key);
        try {
            redisTemplate.opsForHash().putAll(key, map);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * HashSet 并设置时间
     *
     * @param prefix 前缀
     * @param key    键
     * @param map    对应多个键值
     * @param time   时间(秒)
     * @return true成功 false失败
     */

    public Boolean hmset(RedisKeyPrefixEnum prefix, String key, Map<String, Object> map, long time) {
        return hmset(prefix, key, map, time, TimeUnit.MILLISECONDS);
    }


    public Boolean hmset(RedisKeyPrefixEnum prefix, String key, Map<String, Object> map, long time, TimeUnit timeUnit) {
        key = buildKey(prefix, key);
        try {
            redisTemplate.opsForHash().putAll(key, map);
            if (time > 0) {
                expire(prefix, key, time, timeUnit);
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }


    public Boolean hset(RedisKeyPrefixEnum prefix, String key, String item, Object value) {
        key = buildKey(prefix, key);
        try {
            redisTemplate.opsForHash().put(key, item, value);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 向一张hash表中放入数据,如果不存在将创建
     *
     * @param prefix 前缀
     * @param key    键
     * @param item   项
     * @param value  值
     * @param time   时间(秒) 注意:如果已存在的hash表有时间,这里将会替换原有的时间
     * @return true 成功 false失败
     */

    public Boolean hset(RedisKeyPrefixEnum prefix, String key, String item, Object value, long time) {
        return hset(prefix, key, item, value, time, TimeUnit.MILLISECONDS);
    }


    public Boolean hset(RedisKeyPrefixEnum prefix, String key, String item, Object value, long time, TimeUnit timeUnit) {
        key = buildKey(prefix, key);
        try {
            redisTemplate.opsForHash().put(key, item, value);
            if (time > 0) {
                expire(prefix, key, time, timeUnit);
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }


    public void hdel(RedisKeyPrefixEnum prefix, String key, Object... item) {
        key = buildKey(prefix, key);
        redisTemplate.opsForHash().delete(key, item);
    }


    public Boolean hHasKey(RedisKeyPrefixEnum prefix, String key, String item) {
        key = buildKey(prefix, key);
        return redisTemplate.opsForHash().hasKey(key, item);
    }


    public double hincr(RedisKeyPrefixEnum prefix, String key, String item, double by) {
        key = buildKey(prefix, key);
        return redisTemplate.opsForHash().increment(key, item, by);
    }


    public double hdecr(RedisKeyPrefixEnum prefix, String key, String item, double by) {
        key = buildKey(prefix, key);
        return redisTemplate.opsForHash().increment(key, item, by);
    }


    // ============================set=============================


    public Set<String> sGet(RedisKeyPrefixEnum prefix, String key) {
        key = buildKey(prefix, key);
        try {
            Set<Object> sets = redisTemplate.opsForSet().members(key);
            if (sets == null) {
                return null;
            }
            return sets.stream().map(Object::toString).collect(Collectors.toSet());
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }


    public Set<Object> sGetObject(RedisKeyPrefixEnum prefix, String key) {
        key = buildKey(prefix, key);
        return redisTemplate.opsForSet().members(key);
    }


    public <T> Set<T> sGetObject(RedisKeyPrefixEnum prefix, String key, Class<T> clazz) {
        key = buildKey(prefix, key);
        try {
            Set<Object> sets = redisTemplate.opsForSet().members(key);
            Set<T> retSet = sets.stream()
                    .map(o -> {
                        try {
                            return clazz.cast(o);
                        } catch (Exception e) {
                            return null;
                        }
                    }).collect(Collectors.toSet());
            return retSet;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }


    public Boolean sHasKey(RedisKeyPrefixEnum prefix, String key, Object value) {
        key = buildKey(prefix, key);
        try {
            return redisTemplate.opsForSet().isMember(key, value);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }


    public Long sSet(RedisKeyPrefixEnum prefix, String key, Object... values) {
        key = buildKey(prefix, key);
        try {
            return redisTemplate.opsForSet().add(key, values);
        } catch (Exception e) {
            e.printStackTrace();
            return ERROR_CODE;
        }
    }


    public Long sSetAndTime(RedisKeyPrefixEnum prefix, String key, long time, TimeUnit timeUnit, Object... values) {
        key = buildKey(prefix, key);
        try {
            Long count = redisTemplate.opsForSet().add(key, values);
            if (time > 0) {
                expire(prefix, key, time, timeUnit);
            }
            return count;
        } catch (Exception e) {
            e.printStackTrace();
            return ERROR_CODE;
        }
    }


    public Long sGetSetSize(RedisKeyPrefixEnum prefix, String key) {
        key = buildKey(prefix, key);
        try {
            return redisTemplate.opsForSet().size(key);
        } catch (Exception e) {
            e.printStackTrace();
            return ERROR_CODE;
        }
    }


    public Long setRemove(RedisKeyPrefixEnum prefix, String key, Object... values) {
        key = buildKey(prefix, key);
        try {
            return redisTemplate.opsForSet().remove(key, values);
        } catch (Exception e) {
            e.printStackTrace();
            return ERROR_CODE;
        }
    }

    // ===============================list=================================


    public List<String> lGet(RedisKeyPrefixEnum prefix, String key, long start, long end) {
        key = buildKey(prefix, key);
        try {
            List<Object> list = redisTemplate.opsForList().range(key, start, end);
            return list.stream().map(Object::toString).collect(Collectors.toList());
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }


    public List<Object> lGetObject(RedisKeyPrefixEnum prefix, String key, long start, long end) {
        key = buildKey(prefix, key);
        return redisTemplate.opsForList().range(key, start, end);
    }


    public <T> List<T> lGetObject(RedisKeyPrefixEnum prefix, String key, long start, long end, Class<T> clazz) {
        key = buildKey(prefix, key);
        try {
            List<Object> list = redisTemplate.opsForList().range(key, start, end);
            List<T> retList = list.stream()
                    .map(o -> {
                        try {
                            return clazz.cast(o);
                        } catch (Exception e) {
                            e.printStackTrace();
                            return null;
                        }
                    }).collect(Collectors.toList());
            return retList;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }


    public Long lGetListSize(RedisKeyPrefixEnum prefix, String key) {
        key = buildKey(prefix, key);
        try {
            return redisTemplate.opsForList().size(key);
        } catch (Exception e) {
            e.printStackTrace();
            return ERROR_CODE;
        }
    }


    public String lGetIndex(RedisKeyPrefixEnum prefix, String key, long index) {
        try {
            Object o = redisTemplate.opsForList().index(key, index);
            return o instanceof String ? o.toString() : null;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }


    public Object lGetIndexObject(RedisKeyPrefixEnum prefix, String key, long index) {
        return redisTemplate.opsForList().index(key, index);
    }


    public <T> T lGetIndexObject(RedisKeyPrefixEnum prefix, String key, long index, Class<T> clazz) {
        try {
            Object o = redisTemplate.opsForList().index(key, index);
            return clazz.cast(o);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }


    public Boolean lSet(RedisKeyPrefixEnum prefix, String key, Object value) {
        key = buildKey(prefix, key);
        try {
            redisTemplate.opsForList().rightPush(key, value);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }


    public Boolean lSet(RedisKeyPrefixEnum prefix, String key, Object value, long time, TimeUnit timeUnit) {
        key = buildKey(prefix, key);
        try {
            redisTemplate.opsForList().rightPush(key, value);
            if (time > 0) {
                expire(prefix, key, time, timeUnit);
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }

    }


    public Boolean lSet(RedisKeyPrefixEnum prefix, String key, List<Object> value) {
        key = buildKey(prefix, key);
        redisTemplate.opsForList().rightPushAll(key, value);
        return true;

    }


    public Boolean lSet(RedisKeyPrefixEnum prefix, String key, List<Object> value, long time, TimeUnit timeUnit) {
        key = buildKey(prefix, key);
        redisTemplate.opsForList().rightPushAll(key, value);
        if (time > 0) {
            expire(prefix, key, time, timeUnit);
        }
        return true;
    }


    public Boolean lUpdateIndex(RedisKeyPrefixEnum prefix, String key, long index, Object value) {
        key = buildKey(prefix, key);
        redisTemplate.opsForList().set(key, index, value);
        return true;
    }


    public Long lRemove(RedisKeyPrefixEnum prefix, String key, long count, Object value) {
        key = buildKey(prefix, key);
        return redisTemplate.opsForList().remove(key, count, value);
    }
}
