package com.howe.mcp.task;

import com.howe.common.config.ConfigProvider;
import com.howe.common.constant.ConfigConstants;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.scheduling.annotation.Scheduled;

/** 只清理辅助审计，刷新重放标记、OAuth 授权和客户端记录均由独立生命周期规则管理。 */
@Slf4j
@RequiredArgsConstructor
public final class McpAuditCleanupTask {
    private final JdbcOperations jdbc;
    private final ConfigProvider config;
    private final Clock clock;

    /** 每小时最多五批、每批一千条；五秒后不再开始下一批，非法配置时不执行删除。 */
    @Scheduled(fixedDelay = 3600000, initialDelay = 120000)
    public void clean() {
        int total = 0;
        long started = System.nanoTime();
        try {
            String value = config.getPlainValue(ConfigConstants.MCP_LOG_RETENTION_DAYS);
            int days = value == null || value.isBlank() ? 30 : Integer.parseInt(value);
            if (days < 1 || days > 3650) { throw new IllegalArgumentException("审计保留天数无效"); }
            Timestamp cutoff = Timestamp.from(clock.instant().minus(days, ChronoUnit.DAYS));
            for (int batch = 0; batch < 5; batch++) {
                int count = jdbc.update("""
                        delete from mcp_call_log where log_id in (
                            select log_id from (
                                select log_id from mcp_call_log where create_time<?
                                 order by create_time,log_id limit 1000
                            ) expired
                        )
                        """, cutoff);
                total += count;
                if (count < 1000 || System.nanoTime() - started >= TimeUnit.SECONDS.toNanos(5)) { break; }
            }
            if (total > 0) { log.info("MCP 审计清理完成：删除={}", total); }
        } catch (RuntimeException exception) {
            log.warn("MCP 审计清理未完成：已删除={}", total);
        }
    }
}
