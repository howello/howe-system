package com.howe.mcp.service.impl;

import com.howe.mcp.service.McpClientRegistrationService;
import com.howe.mcp.service.McpRegistrationAdmission;

import com.howe.common.config.ConfigProvider;
import com.howe.common.constant.ConfigConstants;
import com.howe.mcp.service.security.McpRegistrationException;
import com.howe.mcp.service.security.McpTokenHash;
import java.time.Clock;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/** 公开登记的共享 IP 配额；Redis 不可用时拒绝，不降级成进程内无界计数。 */
@Slf4j
@RequiredArgsConstructor
public final class McpRegistrationAdmissionImpl implements McpRegistrationAdmission {
    private static final DefaultRedisScript<Long> SCRIPT = new DefaultRedisScript<>("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end
            return count
            """, Long.class);
    private final StringRedisTemplate redis;
    private final ConfigProvider config;
    private final Clock clock;

    /** 在读取登记文档之前消耗每 IP 每分钟配额，键中不保存原始 IP。 */
    @Override
    public void acquire(String ip) {
        String configured = config.getPlainValue(ConfigConstants.MCP_REGISTRATION_IP_PER_MINUTE);
        int maximum = McpClientRegistrationService.parseLimit(configured == null || configured.isBlank() ? "10" : configured, 1, 1000);
        String key = "howe:mcp:registration:" + McpTokenHash.of(ip) + ":" + clock.instant().getEpochSecond() / 60;
        Long count;
        try {
            count = redis.execute(SCRIPT, List.of(key), "120");
        } catch (RuntimeException exception) {
            throw new McpRegistrationException("temporarily_unavailable", 503, "登记限流服务暂不可用");
        }
        if (count == null) {
            throw new McpRegistrationException("temporarily_unavailable", 503, "登记限流服务暂不可用");
        }
        if (count > maximum) {
            throw new McpRegistrationException("temporarily_unavailable", 429, "登记请求过于频繁");
        }
    }
}
