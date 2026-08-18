package com.howe.ai.event;

import com.howe.ai.contract.AiRunEvent;

/** 实时事件发布边界，具体 Redis Stream 适配器位于基础设施层。 */
@FunctionalInterface
public interface RealtimeEventPublisher {
    void publish(AiRunEvent event);
}
