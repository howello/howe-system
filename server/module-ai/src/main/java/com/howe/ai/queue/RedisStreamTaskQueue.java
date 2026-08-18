package com.howe.ai.queue;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import com.howe.ai.contract.AiTaskQueue;

/** Redis Streams 队列，所有命令共享稳定 Stream 和 Consumer Group。 */
@Service
public class RedisStreamTaskQueue implements AiTaskQueue {
    public static final String STREAM_KEY = "ai:run:tasks";
    public static final String DEAD_LETTER_STREAM_KEY = "ai:run:tasks:dead";
    public static final String GROUP = "ai-run-workers";

    private final RedisTemplate<Object, Object> redisTemplate;

    public RedisStreamTaskQueue(RedisTemplate<Object, Object> redisTemplate) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "Redis 模板未配置");
    }

    public RecordId enqueue(AiTaskMessage message) {
        return redisTemplate.opsForStream().add(record(message));
    }

    @Override
    public void enqueue(String runId) {
        enqueue(AiTaskMessage.execute(runId, "run:" + runId, "{}"));
    }

    @Override
    public void resume(String runId) {
        enqueue(AiTaskMessage.resume(runId, "resume:" + runId, "{}"));
    }

    @Override
    public void cancel(String runId) {
        enqueue(AiTaskMessage.cancel(runId, "cancel:" + runId, "{}"));
    }

    public List<MapRecord<Object, Object, Object>> consume(String workerId, int count, Duration block) {
        ensureGroup();
        StreamReadOptions options = StreamReadOptions.empty().count(Math.max(1, count));
        if (block != null && !block.isNegative() && !block.isZero()) options = options.block(block);
        return redisTemplate.opsForStream().read(Consumer.from(GROUP, workerId), options,
            StreamOffset.create(STREAM_KEY, ReadOffset.lastConsumed()));
    }

    public long acknowledge(String recordId) {
        Long acknowledged = redisTemplate.opsForStream().acknowledge(STREAM_KEY, GROUP, recordId);
        return acknowledged == null ? 0 : acknowledged;
    }

    /** 查询待处理消息，供 Recovery 定时任务按 XPENDING/XCLAIM 接管。 */
    public List<MapRecord<Object, Object, Object>> claimExpired(String workerId, Duration minIdle, int count) {
        ensureGroup();
        if (workerId == null || workerId.isBlank()) throw new IllegalArgumentException("Worker 标识不能为空");
        if (minIdle == null || minIdle.isNegative() || minIdle.isZero()) {
            throw new IllegalArgumentException("最小空闲时间必须为正数");
        }
        int safeCount = Math.max(1, Math.min(100, count));
        List<MapRecord<Object, Object, Object>> records = new ArrayList<>();
        var pending = redisTemplate.opsForStream().pending(STREAM_KEY, GROUP,
            org.springframework.data.domain.Range.unbounded(), safeCount);
        if (pending != null) {
            for (var item : pending) {
                if (item.getElapsedTimeSinceLastDelivery().compareTo(minIdle) >= 0) {
                    records.addAll(redisTemplate.opsForStream().claim(STREAM_KEY, GROUP, workerId,
                        minIdle, item.getId()));
                }
            }
        }
        return records;
    }

    public RecordId deadLetter(AiTaskMessage message, String reason) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("command", message.command());
        values.put("runId", message.runId());
        values.put("idempotencyKey", message.idempotencyKey());
        values.put("payload", message.payload());
        values.put("reason", reason == null ? "unknown" : reason);
        return redisTemplate.opsForStream().add(StreamRecords.mapBacked(values).withStreamKey(DEAD_LETTER_STREAM_KEY));
    }

    private MapRecord<Object, Object, Object> record(AiTaskMessage message) {
        Map<Object, Object> values = new LinkedHashMap<>();
        values.put("command", message.command());
        values.put("runId", message.runId());
        values.put("idempotencyKey", message.idempotencyKey());
        values.put("payload", message.payload());
        return StreamRecords.mapBacked(values).withStreamKey(STREAM_KEY);
    }

    private void ensureGroup() {
        try {
            redisTemplate.execute((org.springframework.data.redis.core.RedisCallback<String>) connection ->
                connection.commands().xGroupCreate(STREAM_KEY.getBytes(StandardCharsets.UTF_8),
                    GROUP, ReadOffset.from("0-0"), true));
        } catch (RuntimeException exception) {
            // Redis 在组已存在时返回 BUSYGROUP；并发初始化也安全地走这里。
            // Spring Data Redis 会把 lettuce 的 RedisBusyException 包成 RedisSystemException（message 仅 "Error in execution"），
            // 因此必须遍历异常链查找根因，否则包装后的并发建组会误抛。
            if (containsBusyGroupInChain(exception)) {
                return;
            }
            throw exception;
        }
    }

    private static boolean containsBusyGroupInChain(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (String.valueOf(current.getMessage()).contains("BUSYGROUP")) return true;
            current = current.getCause();
        }
        return false;
    }
}
