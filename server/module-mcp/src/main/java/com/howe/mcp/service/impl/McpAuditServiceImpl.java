package com.howe.mcp.service.impl;

import com.howe.mcp.service.McpAuditService;

import cn.hutool.core.util.IdUtil;
import com.howe.mcp.service.security.McpInvocationContext;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcOperations;

/** 有界辅助审计：只接收固定元数据，不保存请求、凭据、参数、结果正文或异常消息。 */
@Slf4j
public final class McpAuditServiceImpl implements McpAuditService {
    private final JdbcOperations jdbc;
    private final Clock clock;
    private final ThreadPoolExecutor writer;
    private final AtomicLong submitted = new AtomicLong();
    private final AtomicLong written = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();
    private final AtomicLong dropped = new AtomicLong();

    /** 一个写线程和固定队列，JDBC 必须由装配层设置语句超时，不继承请求事务或安全上下文。 */
    public McpAuditServiceImpl(JdbcOperations jdbc, Clock clock, int capacity) {
        if (capacity < 1 || capacity > 1024) { throw new IllegalArgumentException("审计队列容量无效"); }
        this.jdbc = Objects.requireNonNull(jdbc);
        this.clock = Objects.requireNonNull(clock);
        writer = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(capacity), task -> {
            Thread thread = new Thread(task, "mcp-audit");
            thread.setDaemon(true);
            return thread;
        }, new ThreadPoolExecutor.AbortPolicy());
    }

    /** 工具事件只复制已认证上下文中的主键，不让队列持有完整账号或模型参数。 */
    @Override
    public void tool(McpInvocationContext invocation, String knownName, Result result, long elapsedMs) {
        record(new Event(invocation.requestId(), Kind.TOOL_CALL, invocation.userId(), null, invocation.grantId(),
                knownName, result, elapsedMs, clock.instant()));
    }

    /** 提交只执行有界内存操作，队列饱和或服务关闭不阻塞业务线程。 */
    @Override
    public void record(Event event) {
        submitted.incrementAndGet();
        if (event == null || event.kind() == null || event.result() == null || event.occurredAt() == null) {
            drop();
            return;
        }
        var safe = new Event(identifier(event.requestId(), 64) == null ? IdUtil.fastSimpleUUID() : event.requestId(),
                event.kind(), positive(event.userId()), identifier(event.registeredClientId(), 100), positive(event.grantId()),
                identifier(event.toolName(), 64), event.result(), Math.max(0, event.elapsedMs()), event.occurredAt());
        try { writer.execute(() -> write(safe)); }
        catch (RejectedExecutionException exception) { drop(); }
    }

    /** INSERT 独立提交；工具客户端主键只从同一用户的 grant 解析，不反查或复制外部 URL。 */
    private void write(Event event) {
        try {
            jdbc.update("""
                    insert into mcp_call_log(request_id,event_type,user_id,registered_client_id,grant_id,
                                             tool_name,result_code,elapsed_ms,summary,create_time)
                    values(?,?,?,coalesce(?,(select registered_client_id from mcp_grant where grant_id=? and user_id=?)),
                           ?,?,?,?,'',?)
                    """, event.requestId(), event.kind().name(), event.userId(), event.registeredClientId(),
                    event.grantId(), event.userId(), event.grantId(), event.toolName(), event.result().name(), event.elapsedMs(),
                    Timestamp.from(event.occurredAt()));
            written.incrementAndGet();
        } catch (RuntimeException exception) {
            long count = failed.incrementAndGet();
            if (count == 1 || count % 100 == 0) { log.warn("MCP 审计写入失败：累计={}", count); }
        }
    }

    /** 饱和时只累计固定监控指标并采样警告，避免错误洪峰产生同步日志洪峰。 */
    private void drop() {
        long count = dropped.incrementAndGet();
        if (count == 1 || count % 100 == 0) { log.warn("MCP 审计未入队：累计={}", count); }
    }

    /** 只接受有界标识字符，不允许控制字符、任意查询串或正文进入记录。 */
    private static String identifier(String value, int maximum) {
        return value != null && value.length() <= maximum && value.matches("[A-Za-z0-9._:-]+") ? value : null;
    }

    /** 未认证主体保持为空，不从外部表单或用户名猜测账号。 */
    private static Long positive(Long value) { return value != null && value > 0 ? value : null; }

    /** 提供明确的积压、失败和丢弃指标，供管理监控使用。 */
    @Override
    public Statistics statistics() {
        return new Statistics(submitted.get(), written.get(), failed.get(), dropped.get(), writer.getQueue().size(), writer.getActiveCount());
    }

    /** 关闭时最多等待两秒排空，之后中断工作并记录未执行数量，不无界阻塞应用停止。 */
    @Override
    public void close() {
        writer.shutdown();
        try {
            if (writer.awaitTermination(2, TimeUnit.SECONDS)) { return; }
        } catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
        writer.shutdownNow().forEach(task -> drop());
    }
}
