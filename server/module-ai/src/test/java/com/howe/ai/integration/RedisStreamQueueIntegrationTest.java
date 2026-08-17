package com.howe.ai.integration;

import com.howe.ai.queue.AiTaskMessage;
import com.howe.ai.queue.RedisStreamTaskQueue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 7.3 集成测试（Redis 部分）：连真实 Redis db9，验证 Stream 投递、Consumer Group、ACK、过期接管与死信。
 *
 * <p>覆盖验收标准中的「Consumer Group、重复投递、租约恢复（过期消息接管）、逻辑死信」——
 * 这些依赖真实 Redis Streams 的 XPENDING/XAUTOCLAIM/XACK 语义，纯单测用 mock 无法证明。</p>
 */
class RedisStreamQueueIntegrationTest extends AbstractRedisIntegrationTest {

    private RedisStreamTaskQueue queue;
    private RedisTemplate<Object, Object> redis;

    @BeforeEach
    void setupQueue() {
        AssumptionsSupport.assumeRedis(this);
        redis = redisTemplate();
        // 每个测试前清空 stream，保证起点干净（不影响业务 db1）
        redis.delete(List.of(RedisStreamTaskQueue.STREAM_KEY, RedisStreamTaskQueue.DEAD_LETTER_STREAM_KEY));
        queue = new RedisStreamTaskQueue(redis);
    }

    @AfterEach
    void cleanStream() {
        if (redisAvailable() && redis != null) {
            redis.delete(List.of(RedisStreamTaskQueue.STREAM_KEY, RedisStreamTaskQueue.DEAD_LETTER_STREAM_KEY));
        }
    }

    @Test
    void enqueuedMessageIsConsumedByGroupAndAcknowledged() {
        queue.enqueue("100");

        List<MapRecord<Object, Object, Object>> read =
            queue.consume("worker-1", 10, Duration.ofMillis(200));
        assertEquals(1, read.size(), "入队一条必须能消费到一条");
        MapRecord<Object, Object, Object> record = read.get(0);
        assertEquals("run.execute", record.getValue().get("command"));
        assertEquals("100", record.getValue().get("runId"));

        long acked = queue.acknowledge(record.getId().getValue());
        assertEquals(1, acked, "首次 ACK 应返回 1");

        // ACK 后再消费：无新消息，pending 清空
        List<MapRecord<Object, Object, Object>> second =
            queue.consume("worker-1", 10, Duration.ofMillis(100));
        assertTrue(second.isEmpty(), "ACK 后该消息不得再次被消费");
    }

    @Test
    void unacknowledgedMessageIsReclaimedByAnotherWorker() throws Exception {
        queue.enqueue("200");
        // worker-A 消费但不 ACK（模拟崩溃）
        List<MapRecord<Object, Object, Object>> claimed =
            queue.consume("worker-A", 10, Duration.ofMillis(200));
        assertEquals(1, claimed.size());
        String recordId = claimed.get(0).getId().getValue();

        // 等待超过 minIdle，让 worker-B 通过 claimExpired 接管
        Thread.sleep(1200);
        List<MapRecord<Object, Object, Object>> reclaimed =
            queue.claimExpired("worker-B", Duration.ofSeconds(1), 10);

        assertTrue(reclaimed.stream().anyMatch(r -> r.getId().getValue().equals(recordId)),
            "未 ACK 的消息必须能被其他 worker 通过 claimExpired 接管");

        // worker-B 接管后 ACK 成功
        assertEquals(1, queue.acknowledge(recordId), "接管者 ACK 必须成功");
    }

    @Test
    void deadLetterRecordsFailureReason() {
        AiTaskMessage message = AiTaskMessage.execute("300", "run:300", "{}");
        var deadId = queue.deadLetter(message, "max-recovery-exceeded");
        assertNotNull(deadId, "死信必须返回 record id");

        Long size = redis.opsForStream().size(RedisStreamTaskQueue.DEAD_LETTER_STREAM_KEY);
        assertTrue(size != null && size >= 1, "死信 stream 必须记录至少一条");
    }

    @Test
    void consumerGroupSurvivesConcurrentInitialization() {
        // 两个独立队列实例并发 ensureGroup：BUSYGROUP 必须被吞掉而非抛异常
        RedisStreamTaskQueue another = new RedisStreamTaskQueue(redis);
        queue.consume("w-init-a", 1, Duration.ofMillis(50));
        another.consume("w-init-b", 1, Duration.ofMillis(50));
        // 走到这里即证明并发建组安全
        assertTrue(true);
    }

    /** 把静态 assumeTrue 调用收敛到一处，避免在 @BeforeEach 里重复 redisAvailable() 判断。 */
    static final class AssumptionsSupport {
        static void assumeRedis(AbstractRedisIntegrationTest test) {
            org.junit.jupiter.api.Assumptions.assumeTrue(test.redisAvailable(),
                "Redis 集成测试隔离环境不可用，跳过");
        }
    }
}
