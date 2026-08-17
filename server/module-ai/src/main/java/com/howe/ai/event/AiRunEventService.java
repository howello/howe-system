package com.howe.ai.event;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.howe.ai.contract.AiRunEvent;

/** SSE 事件读取服务：按 Last-Event-ID 补发，并按事件 ID 去重。 */
@Service
public class AiRunEventService {
    private final AiEventStore facts;
    private final AiEventStore realtime;

    public AiRunEventService(@Qualifier("aiFactEventStore") AiEventStore facts,
                             @Qualifier("aiRealtimeEventStore") AiEventStore realtime) {
        this.facts = Objects.requireNonNull(facts, "事实事件源不能为空");
        this.realtime = Objects.requireNonNull(realtime, "实时事件源不能为空");
    }

    public List<AiRunEvent> readAfter(String runId, String lastEventId) {
        return readAfterWithStatus(runId, lastEventId).events();
    }

    public EventReadResult readAfterWithStatus(String runId, String lastEventId) {
        List<AiRunEvent> persisted = listEvents(runId, lastEventId);
        List<AiRunEvent> live;
        boolean degraded = false;
        try {
            live = realtime.readAfter(runId, lastEventId);
        } catch (RuntimeException unavailable) {
            live = List.of();
            degraded = true;
        }
        Map<String, AiRunEvent> unique = new LinkedHashMap<>();
        persisted.forEach(event -> unique.put(event.eventId(), event));
        live.forEach(event -> unique.put(event.eventId(), event));
        // 游标解析与事实源保持一致：跨 Run 的 Last-Event-ID 按无游标处理，避免整段跳过。
        long cursor = AiFactEventStore.parseSequence(runId, lastEventId);
        List<AiRunEvent> events = unique.values().stream().filter(event -> event.sequence() > cursor)
            .sorted(Comparator.comparingLong(AiRunEvent::sequence)).toList();
        return new EventReadResult(events, degraded);
    }

    /** 事实源分页读取的稳定命名，Redis 不可用时仍走此路径。 */
    private List<AiRunEvent> listEvents(String runId, String lastEventId) {
        return facts.readAfter(runId, lastEventId);
    }
}
