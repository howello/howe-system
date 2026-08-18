package com.howe.ai.event;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;

import com.howe.ai.contract.AiRunEvent;
import com.howe.ai.persistence.AiFactPersistenceService;

/** MySQL 事实事件源；负责先分配 Run 内序号再持久化事件。 */
@Service("aiFactEventStore")
public class AiFactEventStore implements AiEventStore {
    private final AiFactPersistenceService persistence;

    public AiFactEventStore(AiFactPersistenceService persistence) {
        this.persistence = Objects.requireNonNull(persistence, "AI 持久化服务未配置");
    }

    public AiRunEvent append(String runId, String type, String payload, String idempotencyKey) {
        long sequence = persistence.appendEvent(Long.parseLong(runId), type, payload, idempotencyKey);
        return new AiRunEvent(runId, runId + ":" + sequence, sequence, type, Instant.now(), idempotencyKey, payload);
    }

    @Override
    public List<AiRunEvent> readAfter(String runId, String lastEventId) {
        long sequence = parseSequence(runId, lastEventId);
        return persistence.listEventsAfter(Long.parseLong(runId), sequence, 50).stream()
            .map(AiFactEventStore::toEvent).toList();
    }

    private static AiRunEvent toEvent(Map<String, Object> row) {
        long sequence = ((Number) row.get("sequence_no")).longValue();
        String runId = String.valueOf(row.get("run_id"));
        return new AiRunEvent(runId, runId + ":" + sequence, sequence, String.valueOf(row.get("event_type")),
            toInstant(row.get("create_time")), String.valueOf(row.get("idempotency_key")),
            String.valueOf(row.get("event_json")));
    }

    /**
     * 把 DATETIME 列映射结果转成 Instant，兼容 {@code java.sql.Timestamp}（旧驱动/JDBC 设置）
     * 与 {@code java.time.LocalDateTime}（connector-j 9.x 对 resultType=map 的默认）。
     * 不写死强转，避免在驱动/配置演进时崩溃。
     */
    private static java.time.Instant toInstant(Object value) {
        if (value instanceof java.sql.Timestamp ts) return ts.toInstant();
        if (value instanceof java.time.LocalDateTime ldt) return ldt.atZone(java.time.ZoneOffset.UTC).toInstant();
        if (value instanceof java.time.Instant instant) return instant;
        throw new IllegalStateException("无法识别的 create_time 类型：" + (value == null ? "null" : value.getClass()));
    }

    /**
     * 解析 {@code runId:sequence} 形式的游标，供事实源与实时通道共用。
     *
     * <p>只接受属于同一个 Run 的游标：客户端携带其它 Run 的 Last-Event-ID 时按无游标处理并从头补发，
     * 否则会用别的 Run 的序号把本 Run 的事件整段跳过。</p>
     */
    static long parseSequence(String runId, String lastEventId) {
        if (lastEventId == null || lastEventId.isBlank()) return 0;
        int colon = lastEventId.lastIndexOf(':');
        if (colon < 0) return 0;
        if (!lastEventId.substring(0, colon).equals(runId)) return 0;
        try {
            return Long.parseLong(lastEventId.substring(colon + 1));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }
}
