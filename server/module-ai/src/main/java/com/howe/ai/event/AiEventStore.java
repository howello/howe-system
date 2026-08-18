package com.howe.ai.event;

import java.util.List;

import com.howe.ai.contract.AiRunEvent;

/** 事件读取抽象；MySQL 事实源和 Redis 实时桥均实现此接口。 */
@FunctionalInterface
public interface AiEventStore {
    List<AiRunEvent> readAfter(String runId, String lastEventId);
}
