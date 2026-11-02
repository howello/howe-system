package com.howe.ai.usage;

import com.howe.ai.api.AiErrorCode;
import com.howe.ai.api.AiException;
import com.howe.common.utils.ConfigUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 在 CI 专用 Redis 验证跨实例额度与租约语义，不连接用户运行环境。 */
@EnabledIfEnvironmentVariable(named = "HOWE_TEST_REDIS_HOST", matches = "127\\.0\\.0\\.1")
class AiUsageGuardRedisTest {
    private static LettuceConnectionFactory factory;
    private static StringRedisTemplate redis;

    /** 仅使用工作流创建的本地测试服务。 */
    @BeforeAll
    static void connect() {
        factory = new LettuceConnectionFactory("127.0.0.1", 6379);
        factory.afterPropertiesSet();
        factory.start();
        redis = new StringRedisTemplate(factory);
        redis.afterPropertiesSet();
    }

    /** 释放测试连接池。 */
    @AfterAll
    static void disconnect() {
        factory.destroy();
    }

    /** 两个门面实例共用并发边界，拒绝不扣受理次数，完成后释放名额。 */
    @Test
    void sharesLimitsAcrossInstancesAndReleasesLeases() {
        String id = UUID.randomUUID().toString();
        String[] identity = { "user:" + id, "family:" + id };
        AiUsageGuard first = new AiUsageGuard(redis);
        AiUsageGuard second = new AiUsageGuard(redis);
        try (var config = mockStatic(ConfigUtils.class)) {
            config.when(() -> ConfigUtils.getInt(anyString(), anyInt())).thenAnswer(call -> {
                String key = call.getArgument(0);
                return key.endsWith("Concurrency") ? 1 : 2;
            });
            try (var lease = first.acquire("chat", identity)) {
                AiException error = assertThrows(AiException.class, () -> second.acquire("chat", identity));
                assertEquals(AiErrorCode.RATE_LIMITED, error.getAiErrorCode());
            }
            try (var lease = second.acquire("chat", identity)) {
                assertNotNull(lease);
            }
            AiException exhausted = assertThrows(AiException.class, () -> first.acquire("chat", identity));
            assertEquals(AiErrorCode.QUOTA_EXCEEDED, exhausted.getAiErrorCode());
        } finally {
            var keys = redis.keys("{ai:usage}:chat:*" + id);
            if (keys != null && !keys.isEmpty()) redis.delete(keys);
        }
    }
}
