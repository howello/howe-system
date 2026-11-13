package com.howe.mcp.operation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

/** 本地操作恢复调度；复用执行器的当前处理器、权限及原子提交路径。 */
@Slf4j
@RequiredArgsConstructor
public final class McpOperationRecoveryTask {

    private final McpOperationExecutor executor;

    /** 有界恢复到期操作，单轮失败不阻断后续调度。 */
    @Scheduled(fixedDelay = 15000)
    public void recover() {
        try {
            executor.recoverOnce();
        } catch (RuntimeException exception) {
            log.warn("MCP 操作恢复未完成，下轮继续，类型={}", exception.getClass().getSimpleName());
        }
    }
}
