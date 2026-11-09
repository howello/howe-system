package com.howe.mcp.task;

import com.howe.mcp.service.McpCredentialCleanupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

/** 凭据和闲置登记的定时清理，不依赖 MCP 运行开关，安装后才装配。 */
@Slf4j
@RequiredArgsConstructor
public final class McpCredentialCleanupTask {
    private final McpCredentialCleanupService service;
    /** 每小时执行有界一轮；日志只输出计数，不输出异常、用户或索引内容。 */
    @Scheduled(fixedDelay = 3600000, initialDelay = 180000)
    public void clean() {
        try {
            var result = service.clean();
            if (result.authorizations() + result.tokens() + result.unusedClients() > 0) {
                log.info("MCP 凭据清理完成：授权={}，索引={}，闲置客户端={}", result.authorizations(), result.tokens(), result.unusedClients());
            }
        } catch (RuntimeException exception) { log.warn("MCP 凭据清理未完成；已提交批次保留，下轮继续"); }
    }
}
