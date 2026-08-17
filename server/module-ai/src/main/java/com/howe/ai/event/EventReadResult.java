package com.howe.ai.event;

import java.util.List;

import com.howe.ai.contract.AiRunEvent;

/** SSE 读取结果，明确是否因实时桥不可用而降级到事实源。 */
public record EventReadResult(List<AiRunEvent> events, boolean degraded) {
    public EventReadResult {
        events = List.copyOf(events);
    }
}
