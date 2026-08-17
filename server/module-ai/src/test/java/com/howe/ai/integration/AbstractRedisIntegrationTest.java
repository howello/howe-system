package com.howe.ai.integration;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 集成测试基类：连真实 Redis 的隔离 db（默认 9，业务用 1），绝不触碰业务数据。
 *
 * <p>连接优先读环境变量 {@code AI_ITEST_REDIS_HOST} / {@code AI_ITEST_REDIS_PORT} /
 * {@code AI_ITEST_REDIS_PASSWORD} / {@code AI_ITEST_REDIS_DATABASE}，缺省回落本机 db9。
 * Redis 不可达时用 {@link Assumptions#assumeTrue(boolean)} 跳过整个测试类——客观环境阻断而非断言被跳过。</p>
 *
 * <p>测试共用 {@link RedisStreamTaskQueue} 的固定 stream key（{@code ai:run:tasks} 等），
 * 因此测试类在 {@link AfterAll} 里清空这些 stream，避免残留消息污染后续测试与业务侧（业务在 db1 不受影响）。</p>
 */
public abstract class AbstractRedisIntegrationTest {

    private static volatile LettuceConnectionFactory CONNECTION_FACTORY;
    private static volatile RedisTemplate<Object, Object> REDIS_TEMPLATE;
    private static volatile boolean AVAILABLE;

    protected static RedisTemplate<Object, Object> redisTemplate() {
        return REDIS_TEMPLATE;
    }

    protected static boolean redisAvailable() {
        return AVAILABLE;
    }

    @BeforeAll
    static void connectIsolatedRedis() {
        if (REDIS_TEMPLATE != null) return;
        synchronized (AbstractRedisIntegrationTest.class) {
            if (REDIS_TEMPLATE != null) return;
            String host = System.getenv().getOrDefault("AI_ITEST_REDIS_HOST", "localhost");
            int port = Integer.parseInt(System.getenv().getOrDefault("AI_ITEST_REDIS_PORT", "6379"));
            String password = System.getenv("AI_ITEST_REDIS_PASSWORD");
            int database = Integer.parseInt(System.getenv().getOrDefault("AI_ITEST_REDIS_DATABASE", "9"));

            RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(host, port);
            config.setDatabase(database);
            if (password != null && !password.isEmpty()) config.setPassword(password);
            LettuceConnectionFactory factory = new LettuceConnectionFactory(config);
            factory.afterPropertiesSet();
            try {
                factory.getConnection().ping();
            } catch (Exception e) {
                factory.destroy();
                AVAILABLE = false;
                Assumptions.assumeTrue(false, "集成测试隔离 Redis db" + database + " 不可达，跳过（环境阻断）");
                return;
            }
            CONNECTION_FACTORY = factory;
            RedisTemplate<Object, Object> template = new RedisTemplate<>();
            template.setConnectionFactory(factory);
            template.setKeySerializer(new StringRedisSerializer());
            template.setHashKeySerializer(new StringRedisSerializer());
            template.setValueSerializer(new StringRedisSerializer());
            template.setHashValueSerializer(new StringRedisSerializer());
            template.afterPropertiesSet();
            REDIS_TEMPLATE = template;
            AVAILABLE = true;
        }
    }

    @AfterAll
    static void cleanupIsolatedRedisStreams() {
        if (!AVAILABLE) return;
        // 清空测试用过的固定 stream key，避免残留；只动 db9，业务 db1 不受影响
        try {
            REDIS_TEMPLATE.delete(java.util.List.of(
                com.howe.ai.queue.RedisStreamTaskQueue.STREAM_KEY,
                com.howe.ai.queue.RedisStreamTaskQueue.DEAD_LETTER_STREAM_KEY));
        } catch (Exception ignored) {
            // 清理失败不阻塞测试结论
        }
    }

    @AfterAll
    static void shutdownRedis() {
        if (CONNECTION_FACTORY != null) CONNECTION_FACTORY.destroy();
    }
}
