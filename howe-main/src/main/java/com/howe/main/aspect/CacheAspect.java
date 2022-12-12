package com.howe.main.aspect;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.howe.common.annotation.Cache;
import com.howe.common.enums.exception.MainExceptionEnum;
import com.howe.common.enums.redis.RedisKeyPrefixEnum;
import com.howe.common.enums.redis.RedisTypeEnum;
import com.howe.common.exception.child.MainException;
import com.howe.common.utils.CommonUtils;
import com.howe.common.utils.redis.RedisUtils;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

/**
 * <p>@Author lu
 * <p>@Date 2022/12/12 15:18 星期一
 * <p>@Version 1.0
 * <p>@Description 缓存切面
 */
@Slf4j
@Order(99)
@Component
@Aspect
@RequiredArgsConstructor
public class CacheAspect {

    private final RedisUtils redisUtils;

    @Pointcut("@annotation(com.howe.common.annotation.Cache)")
    public void point() {
    }

    @SneakyThrows
    @Around("point()")
    public Object doAround(ProceedingJoinPoint joinPoint) {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        Cache cache = method.getAnnotation(Cache.class);
        RedisKeyPrefixEnum prefix = cache.prefix();
        String key = null;
        Object[] args = joinPoint.getArgs();
        Object obj = args[cache.indexArgs()];
        if (CommonUtils.isPrimitive(obj.getClass())) {
            key = String.valueOf(obj);
        } else {
            key = getKey(cache.key(), obj);
        }
        if (StringUtils.isBlank(key)) {
            key = cache.key();
        }
        RedisTypeEnum redisType = cache.redisType();
        Class<?> clazz = cache.clazz();
        switch (cache.redisType()) {
            case STRING:
                String s = redisUtils.get(prefix, key);
                if (StringUtils.isNotBlank(s)) {
                    return s;
                }
                break;
            case LIST:
                List<?> list = redisUtils.getListObject(prefix, key, clazz);
                if (CollectionUtils.isNotEmpty(list)) {
                    return list;
                }
                break;
            case SET:
                Set<?> set = redisUtils.sGetObject(prefix, key, clazz);
                if (CollectionUtils.isNotEmpty(set)) {
                    return set;
                }
                break;
            case OBJECT:
                Object object = redisUtils.getObject(prefix, key, clazz);
                if (object != null) {
                    return object;
                }
                break;
            default:
                throw new MainException(MainExceptionEnum.END);
        }
        Object ret = joinPoint.proceed();
        switch (cache.redisType()) {
            case LIST:
                redisUtils.setList(prefix, key, (List) ret);
                break;
            case SET:
                redisUtils.sSet(prefix, key, (Set) ret);
                break;
            case STRING:
            default:
                redisUtils.set(prefix, key, ret);
                break;
        }
        int time = cache.time();
        if (time > 0) {
            redisUtils.expire(prefix, key, time, cache.timeUnit());
        }
        return ret;
    }

    private String getKey(String key, Object obj) {
        String json = JSONObject.toJSONString(obj);
        if (json.startsWith("{") && json.endsWith("}")) {
            JSONObject jsonObject = JSONObject.parseObject(json);
            if (jsonObject.containsKey(key)) {
                return jsonObject.getString(key);
            }
            for (Object value : jsonObject.values()) {
                if (!value.getClass().isPrimitive()) {
                    return getKey(key, value);
                }
            }

        } else if (json.startsWith("[") && json.endsWith("]")) {
            JSONArray array = JSONArray.parseArray(json);
            for (Object o : array) {
                return getKey(key, o);
            }
        }
        return null;
    }
}
