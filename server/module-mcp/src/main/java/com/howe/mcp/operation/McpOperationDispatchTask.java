package com.howe.mcp.operation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 操作派发与清理定时任务。
 *
 * <p>持久化的 {@code QUEUED} 行才是派发事实；内存唤醒只是加速。任务每秒扫一轮，
 * 每轮有界；异常只记录固定日志，不吞掉已提交状态。</p>
 */
@Slf4j
@RequiredArgsConstructor
public final class McpOperationDispatchTask {

    private final McpOperationExecutor executor;

    private final com.howe.mcp.operation.McpOperationStepExecutor stepExecutor;

    /** 每秒派发一轮；单轮最多领取执行器内置批量。 */
    @Scheduled(fixedDelay = 1000, initialDelay = 5000)
    public void dispatch() {
        try {
            executor.dispatchOnce();
        } catch (RuntimeException exception) {
            log.warn("MCP 写操作派发未完成，下轮继续");
        }
        try {
            // 后续步骤与父操作共用同一调度节奏，不另建独立扫描线程。
            stepExecutor.dispatchOnce();
        } catch (RuntimeException exception) {
            log.warn("MCP 后续步骤派发未完成，下轮继续");
        }
    }

    /** 每十分钟有界清理已知终态的过期账本；UNKNOWN 永不按时间清理。 */
    @Scheduled(fixedDelay = 600000, initialDelay = 120000)
    public void cleanup() {
        try {
            executor.cleanup();
        } catch (RuntimeException exception) {
            log.warn("MCP 写操作清理未完成，下轮继续");
        }
    }
}
