package com.howe.ai.worker;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.stereotype.Service;

import com.howe.ai.queue.AiTaskMessage;
import com.howe.ai.queue.RedisStreamTaskQueue;

/** 消费 Redis Stream 任务；只有 Worker 成功处理后才 ACK，异常消息保留在 pending 集合。 */
@Service
public class AiRunStreamConsumer {
    private final RedisStreamTaskQueue queue;
    private final AiRunWorker worker;

    public AiRunStreamConsumer(RedisStreamTaskQueue queue, AiRunWorker worker) {
        this.queue = Objects.requireNonNull(queue, "AI 任务队列未配置");
        this.worker = Objects.requireNonNull(worker, "AI Worker 未配置");
    }

    public int consumeOnce(String workerId, int count, Duration block, Duration leaseDuration) {
        int acknowledged = 0;
        List<MapRecord<Object, Object, Object>> records = queue.consume(workerId, count, block);
        for (MapRecord<Object, Object, Object> record : records) {
            Map<Object, Object> values = record.getValue();
            AiTaskMessage message = new AiTaskMessage(String.valueOf(values.get("command")),
                String.valueOf(values.get("runId")), String.valueOf(values.get("idempotencyKey")),
                String.valueOf(values.getOrDefault("payload", "{}")));
            if (worker.handle(message, workerId, leaseDuration)) {
                acknowledged += (int) queue.acknowledge(record.getId().getValue());
            }
        }
        return acknowledged;
    }
}
