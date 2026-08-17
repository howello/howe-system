package com.howe.ai.event;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import com.howe.ai.contract.AiRunEvent;

/**
 * Redis 实时事件通道：只承载短期数据，事实源仍是 MySQL。
 *
 * <p>每个 Run 一条 Stream 并按上限裁剪，裁剪掉的历史由 {@link AiFactEventStore} 补发；
 * Redis 不可用时抛出异常，由 {@link AiRunEventService} 转为降级读取而不是静默丢事件。</p>
 */
@Service("aiRealtimeEventStore")
public class RedisRunEventStore implements AiEventStore, RealtimeEventPublisher {
    private static final String KEY_PREFIX = "ai:run:events:";
    private static final int MAX_PAGE_SIZE = 50;

    private final RedisTemplate<Object, Object> redisTemplate;
    private final long maxStreamLength;

    public RedisRunEventStore(RedisTemplate<Object, Object> redisTemplate,
                              @Value("${ai.event.redis-stream-length:200}") long maxStreamLength) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "Redis 模板未配置");
        this.maxStreamLength = maxStreamLength < 1 ? 200 : maxStreamLength;
    }

    @Override
    public void publish(AiRunEvent event) {
        Objects.requireNonNull(event, "运行事件不能为空");
        String key = key(event.runId());
        Map<Object, Object> values = new LinkedHashMap<>();
        values.put("runId", event.runId());
        values.put("eventId", event.eventId());
        values.put("sequence", String.valueOf(event.sequence()));
        values.put("type", event.type());
        values.put("occurredAt", event.occurredAt().toString());
        values.put("idempotencyKey", event.idempotencyKey());
        values.put("payload", event.payload());
        redisTemplate.opsForStream().add(StreamRecords.mapBacked(values).withStreamKey(key));
        redisTemplate.opsForStream().trim(key, maxStreamLength, true);
    }

    @Override
    public List<AiRunEvent> readAfter(String runId, String lastEventId) {
        long cursor = AiFactEventStore.parseSequence(runId, lastEventId);
        List<MapRecord<Object, Object, Object>> records =
            redisTemplate.opsForStream().range(key(runId), Range.unbounded());
        if (records == null) return List.of();
        return records.stream().map(RedisRunEventStore::toEvent)
            .filter(event -> event != null && event.sequence() > cursor)
            .sorted(java.util.Comparator.comparingLong(AiRunEvent::sequence))
            .limit(MAX_PAGE_SIZE).toList();
    }

    private static AiRunEvent toEvent(MapRecord<Object, Object, Object> record) {
        Map<Object, Object> values = record.getValue();
        String sequence = text(values.get("sequence"));
        if (sequence.isBlank()) return null;
        try {
            return new AiRunEvent(text(values.get("runId")), text(values.get("eventId")), Long.parseLong(sequence),
                text(values.get("type")), Instant.parse(text(values.get("occurredAt"))),
                text(values.get("idempotencyKey")), text(values.get("payload")));
        } catch (RuntimeException malformed) {
            // 实时通道中的脏数据不能影响补发，事实源仍可提供完整序列。
            return null;
        }
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String key(String runId) {
        return KEY_PREFIX + runId;
    }
}
