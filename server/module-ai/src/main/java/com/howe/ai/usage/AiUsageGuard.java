package com.howe.ai.usage;

import cn.hutool.core.util.IdUtil;
import com.howe.ai.api.AiErrorCode;
import com.howe.ai.api.AiException;
import com.howe.common.constant.ConfigConstants;
import com.howe.common.core.domain.model.LoginUser;
import com.howe.common.utils.ConfigUtils;
import com.howe.common.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** 用户和家庭的 AI 受理次数与并发租约，所有实例在 Redis 中原子裁决。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiUsageGuard {
    private final StringRedisTemplate redis;
    private static final DefaultRedisScript<Long> ACQUIRE = new DefaultRedisScript<>("""
            local t = redis.call('TIME')
            local now = tonumber(t[1])*1000 + math.floor(tonumber(t[2])/1000)
            for i=3,5 do redis.call('ZREMRANGEBYSCORE', KEYS[i], '-inf', now) end
            if tonumber(redis.call('GET',KEYS[1]) or '0') >= tonumber(ARGV[1]) then return 1 end
            if tonumber(redis.call('GET',KEYS[2]) or '0') >= tonumber(ARGV[2]) then return 2 end
            if redis.call('ZCARD',KEYS[3]) >= tonumber(ARGV[3]) then return 3 end
            if redis.call('ZCARD',KEYS[4]) >= tonumber(ARGV[4]) then return 4 end
            if redis.call('ZCARD',KEYS[5]) >= tonumber(ARGV[5]) then return 5 end
            for i=1,2 do redis.call('INCR',KEYS[i]); redis.call('EXPIRE',KEYS[i],172800) end
            for i=3,5 do
              redis.call('ZADD',KEYS[i],now+3600000,ARGV[6]); redis.call('EXPIRE',KEYS[i],3600)
            end
            return 0
            """, Long.class);
    private static final DefaultRedisScript<Long> RELEASE = new DefaultRedisScript<>("""
            for i=1,3 do redis.call('ZREM',KEYS[i],ARGV[1]) end; return 1
            """, Long.class);

    /** 捕获当前调用者，后台任务使用独立 system 预算，不能无限调用。 */
    public String[] identity() {
        var auth = SecurityUtils.getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof LoginUser user) {
            return new String[] { "user:" + user.getUserId(), "family:" + user.getDeptId() };
        }
        return new String[] { "user:system", "family:system" };
    }

    /** 预占一次受理额度；包含失败的实际尝试，重试不重复预占，拒绝不会扣额度。 */
    public Lease acquire(String capability, String[] identity) {
        String day = LocalDate.now(ZoneId.of("Asia/Shanghai")).toString();
        String prefix = "{ai:usage}:" + capability + ":";
        List<String> keys = List.of(prefix + "daily:" + day + ":" + identity[0],
                prefix + "daily:" + day + ":" + identity[1],
                prefix + "active:" + identity[0], prefix + "active:" + identity[1], prefix + "active:global");
        boolean image = "image".equals(capability);
        String config = ConfigConstants.AI_USAGE_PREFIX + capability + ".";
        String id = IdUtil.fastSimpleUUID();
        try {
            Long result = redis.execute(ACQUIRE, keys,
                    limit(config + "userDailyLimit", image ? 20 : 100),
                    limit(config + "familyDailyLimit", image ? 100 : 500),
                    limit(config + "userConcurrency", 2), limit(config + "familyConcurrency", 4),
                    limit(config + "globalConcurrency", image ? 4 : 8), id);
            if (result == null) throw new IllegalStateException("Redis 未返回额度判定");
            if (result == 1 || result == 2) throw new AiException(AiErrorCode.QUOTA_EXCEEDED, "今日 AI 受理次数已达上限");
            if (result != 0) throw new AiException(AiErrorCode.RATE_LIMITED, "AI 正在处理其他任务，请稍后重试");
            return new Lease(keys.subList(2, 5), id);
        } catch (AiException e) {
            throw e;
        } catch (Exception e) {
            throw new AiException(AiErrorCode.RATE_LIMITED, "AI 额度服务暂不可用，请稍后重试", e);
        }
    }

    /** 读取正数限制，错误配置不能静默变成无限调用。 */
    private String limit(String key, int fallback) {
        return Integer.toString(Math.max(1, Math.min(1_000_000, ConfigUtils.getInt(key, fallback))));
    }

    /** 一次调用的并发租约，完成、失败和取消时都释放，进程崩溃由 TTL 兜底。 */
    public final class Lease implements AutoCloseable {
        private final List<String> keys;
        private final String id;
        private final AtomicBoolean closed = new AtomicBoolean();

        /** 保存并发租约标识，不保留提示词等业务内容。 */
        private Lease(List<String> keys, String id) {
            this.keys = keys;
            this.id = id;
        }

        /** 幂等释放并发名额，Redis 失败不覆盖模型调用的原始结果。 */
        @Override
        public void close() {
            if (!closed.compareAndSet(false, true)) return;
            try {
                redis.execute(RELEASE, keys, id);
            } catch (Exception e) {
                log.warn("释放 AI 并发租约失败，将由过期时间兜底", e);
            }
        }
    }
}
