package com.howe.common.annotation;

import com.howe.common.enums.redis.RedisKeyPrefixEnum;
import com.howe.common.enums.redis.RedisTypeEnum;

import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;

/**
 * <p>@Author lu
 * <p>@Date 2022/12/12 15:08 星期一
 * <p>@Version 1.0
 * <p>@Description 生成缓存的注解
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Cache {

    /**
     * 缓存前缀
     *
     * @return
     */
    RedisKeyPrefixEnum prefix();

    /**
     * 第几个参数，如果是基础类型，直接取，不是循环取
     *
     * @return
     */
    int indexArgs() default 0;

    /**
     * 缓存的key
     * 如果入参有这个字典，取值。
     * 没有这个字段，取固定值
     * 如果入参是对象，取对象
     *
     * @return
     */
    String key() default "";

    /**
     * 存入redis的类型
     *
     * @return
     */
    RedisTypeEnum redisType() default RedisTypeEnum.STRING;

    /**
     * 如果为自定义类，需要类名
     *
     * @return
     */
    Class<?> clazz() default String.class;

    /**
     * 延迟 默认永久
     *
     * @return
     */
    int time() default -1;

    /**
     * 时间单位，默认毫秒
     *
     * @return
     */
    TimeUnit timeUnit() default TimeUnit.MILLISECONDS;
}
