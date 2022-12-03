package com.howe.common.utils.id;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.RandomUtil;
import com.howe.common.enums.redis.RedisKeyPrefixEnum;
import com.howe.common.utils.redis.RedisUtils;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * <p>@Author lu
 * <p>@Date 2022/11/22 11:22 星期二
 * <p>@Version 1.0
 * <p>@Description TODO
 */
@Component
public class IdGeneratorUtil {

    @Resource
    private RedisUtils redisUtils;

    private static final int TIME_BITS = 30;
    private static final int RAND1_BITS = 20;
    private static final int RAND2_BITS = 16;
    private static final int RAND1_LIMIT = 2 ^ 10;
    private static final int RAND2_LIMIT = 2 ^ 4;


    /**
     * <p>总共生成二进制64位Long型数据。</p>
     * <p>0000000000000000000000000000000000000000000000000000000000000000</p>
     * <p>|1|          33                  |   10    |   10    |   10    |</p>
     * <p>第1位 ： 符号位 永远为0</p>
     * <p>后面33位 ： 时间戳，秒级。从1970年开始，可以用到2242年。</p>
     * <p>后面10位 ： 第一个随机数，总长10位，[0,1024)</p>
     * <p>后面10位 ： 第二个随机数，总长4位，[0,16)</p>
     * <p>后面10位 ： redis自增数据，总长16位，就是说每秒最多可生成65536个不同的id。</p>
     *
     * <p>加上前面两个随机数，id重复及机率几乎为0。</p>
     * <p>即使调时间+清redis缓存二者同时进行，id重复的机率也很小。</p>
     *
     * @return id
     */
    public long next() {
        // 获取当前时间
        LocalDateTime now = LocalDateTime.now();
        long timeStamp = now.toEpochSecond(ZoneOffset.UTC);
        // 组装成key=prefix:2022:01:01(组装成这种形式方便日后根据日期统计当天的订单数量)
        String key = DateUtil.format(now, "yyyy:MM:dd:HH:mm");
        // 订单自增长
        Long incr = redisUtils.incr(RedisKeyPrefixEnum.ID_GENERATOR_KEY, key, 1);
        long rand1 = RandomUtil.randomLong(RAND1_LIMIT);
        long rand2 = RandomUtil.randomLong(RAND2_LIMIT);
        return timeStamp << TIME_BITS | rand1 << RAND1_BITS | rand2 << RAND2_BITS | incr;
    }

    /**
     * 16禁止的id，比较短，带字母
     *
     * @return
     */
    public String nextHex() {
        long next = this.next();
        return Long.toHexString(next);
    }
}
