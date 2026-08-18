package com.howe.ai.controller;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.howe.ai.event.RunEventStreamWriter;
import com.howe.ai.persistence.AiFactPersistenceService;
import com.howe.common.utils.SecurityUtils;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 鉴权的 SSE 事件流入口；只读取事件，不在服务端重试 Tool，也不改变 Run 状态。
 *
 * <p>直接写入 Servlet 响应保持长连接：module-ai 只依赖 module-common，classpath 上没有
 * spring-webmvc，因而不能使用 SseEmitter；推送、补发与去重的语义由
 * {@link RunEventStreamWriter} 承担并独立测试。</p>
 */
@RestController
@RequestMapping("/ai/admin/runs")
@Tag(name = "管理员助手事件")
public class AiSseController {
    private final RunEventStreamWriter stream;
    private final AiFactPersistenceService persistence;

    public AiSseController(RunEventStreamWriter stream, AiFactPersistenceService persistence) {
        this.stream = stream;
        this.persistence = persistence;
    }

    @GetMapping(value = "/{runId}/events/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("@ss.hasPermi('ai:run:view')")
    @Operation(summary = "订阅运行事件")
    public void events(@Parameter(description = "Run ID") @PathVariable long runId,
                       @Parameter(description = "最后收到的事件 ID")
                       @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId,
                       HttpServletResponse response) throws IOException {
        persistence.requireRunAccess(runId, SecurityUtils.getUserId());
        response.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Cache-Control", "no-cache");
        // 关闭反向代理缓冲，否则事件会被攒到连接结束才下发。
        response.setHeader("X-Accel-Buffering", "no");
        PrintWriter writer = response.getWriter();
        stream.stream(runId, lastEventId, writer, () -> !writer.checkError());
    }
}
