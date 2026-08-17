package com.howe.ai.event;

import com.howe.ai.contract.AiRunEvent;

/**
 * Run 事件发射边界。
 *
 * <p>序号必须由 MySQL 事实源分配，Harness 不得自行维护计数器，否则重启或并发时会出现重复或跳号。
 * 生产实现为 {@link RedisEventBridge}：先写入事实表拿到 Run 内单调序号，再桥接到实时通道。</p>
 */
@FunctionalInterface
public interface RunEventEmitter {
    AiRunEvent publish(String runId, String type, String payload, String idempotencyKey);
}
