package com.howe.mcp.service.impl;

import com.howe.mcp.service.McpProtocolAdmission;

import com.howe.common.config.ConfigProvider;
import com.howe.common.constant.ConfigConstants;
import com.howe.mcp.service.security.McpTokenHash;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/** 协议请求使用共享 Redis 原子窗口；窗口由 Redis TTL 决定，不依赖各应用机器时钟。 */
@Slf4j
@RequiredArgsConstructor
public final class McpProtocolAdmissionImpl implements McpProtocolAdmission {
    private static final DefaultRedisScript<Long> SCRIPT = new DefaultRedisScript<>("""
            local count = tonumber(redis.call('GET', KEYS[1]) or '0')
            if count >= tonumber(ARGV[1]) then
                local ttl = redis.call('PTTL', KEYS[1])
                if ttl < 0 then redis.call('PEXPIRE', KEYS[1], 60000); ttl = 60000 end
                return math.max(1, math.ceil(ttl / 1000))
            end
            local next = redis.call('INCR', KEYS[1])
            if next == 1 or redis.call('PTTL', KEYS[1]) < 0 then redis.call('PEXPIRE', KEYS[1], 60000) end
            return 0
            """, Long.class);
    private final StringRedisTemplate redis;
    private final ConfigProvider config;

    /** 消耗请求额度；仅存主体哈希，不保存原始 IP 或任何令牌。 */
    @Override
    public void acquire(Bucket bucket, String subject) {
        try {
            if (subject == null || subject.isBlank() || subject.length() > 256) {
                throw new IllegalArgumentException("限流主体无效");
            }
            String configured = config.getPlainValue(ConfigConstants.MCP_RATE_PREFIX + bucket.key() + "PerMinute");
            int maximum = configured == null || configured.isBlank() ? bucket.maximum() : Integer.parseInt(configured);
            if (maximum < 1 || maximum > 100000) { throw new IllegalArgumentException("限流额度无效"); }
            Long retry = redis.execute(SCRIPT, List.of("howe:mcp:rate:" + bucket.key() + ":" + McpTokenHash.of(subject)),
                    Integer.toString(maximum));
            if (retry == null || retry < 0 || retry > 60) { throw new IllegalStateException("限流响应无效"); }
            if (retry > 0) { throw new Rejected(429, retry.intValue()); }
        } catch (Rejected exception) {
            throw exception;
        } catch (RuntimeException exception) {
            log.warn("MCP 协议限流暂不可用：bucket={}", bucket);
            throw new Rejected(503, 0);
        }
    }

}
