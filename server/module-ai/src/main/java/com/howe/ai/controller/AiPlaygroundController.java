package com.howe.ai.controller;

import com.howe.ai.api.AiClient;
import com.howe.ai.api.dto.AiChatRequest;
import com.howe.ai.api.dto.AiEmbedRequest;
import com.howe.ai.api.dto.AiImageRequest;
import com.howe.ai.api.dto.AiTextChunk;
import com.howe.ai.api.dto.AiVisionRequest;
import com.howe.ai.config.AiProperties;
import com.howe.common.core.controller.BaseController;
import com.howe.common.core.domain.AjaxResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * AI 接口调试台
 *
 * <p>后台用来验证网关链路：直接指定渠道与模型，绕开场景路由，把 {@link AiClient}
 * 的四种能力各跑一遍。请求与响应就是业务模块看到的契约本身，所以这个页面同时
 * 也是一份可运行的接入示例。</p>
 *
 * <p>刻意不加 {@code @Log}：每次调用已经由网关写进 {@code ai_call_log}，
 * 再记一条操作日志只会污染 {@code sys_oper_log}。</p>
 *
 * <p>注意这里的调用走的是真实路由，所以受 {@code ai.enabled} 总开关约束，
 * 关着的时候会直接返回 {@code AI_DISABLED}。</p>
 *
 * @author howe
 */
@Slf4j
@Tag(name = "AI 接口调试", description = "按渠道与模型直接调用，验证网关链路")
@RestController
@RequestMapping("/ai/playground")
@RequiredArgsConstructor
public class AiPlaygroundController extends BaseController
{
    private final AiClient aiClient;

    /**
     * 文本对话（同步）
     */
    @Operation(summary = "文本对话", description = "同步返回完整结果")
    @PreAuthorize("@ss.hasPermi('ai:playground:test')")
    @PostMapping("/chat")
    public AjaxResult chat(@RequestBody AiChatRequest request)
    {
        return success(aiClient.chat(request));
    }

    /**
     * 文本对话（流式）
     *
     * <p>路由阶段就失败（总开关关闭、模型不可用等）时 {@code AiClient.chatStream}
     * 会直接抛异常，此时由全局异常处理返回普通 JSON，前端按响应类型分支处理。</p>
     */
    @Operation(summary = "文本对话（流式）",
            description = "SSE 推送 AiTextChunk，最后一个分片 done=true；路由阶段失败时返回普通 JSON 错误")
    @PreAuthorize("@ss.hasPermi('ai:playground:test')")
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestBody AiChatRequest request, HttpServletResponse response)
    {
        // nginx 默认会缓冲被代理的响应，流式内容会被攒成一整块再吐给前端，
        // 表现就是「一直转圈、最后一次性出现」。这个响应头让 nginx 对本次响应关掉缓冲；
        // 其它网关不认识它，忽略即可，不会有副作用。
        response.setHeader("X-Accel-Buffering", "no");
        SseEmitter emitter = new SseEmitter(AiProperties.load().getTimeoutChatMs());
        AtomicReference<Disposable> subscription = new AtomicReference<>();
        AtomicInteger pushed = new AtomicInteger();
        AtomicLong firstPushAt = new AtomicLong();
        long start = System.currentTimeMillis();
        // 客户端断开时停掉上游，别让模型继续生成白白消耗 token
        emitter.onCompletion(() -> dispose(subscription));
        emitter.onTimeout(() -> dispose(subscription));
        emitter.onError(e -> dispose(subscription));
        subscription.set(aiClient.chatStream(request).subscribe(
                chunk ->
                {
                    firstPushAt.compareAndSet(0, System.currentTimeMillis() - start);
                    pushed.incrementAndGet();
                    push(emitter, chunk, subscription);
                },
                error ->
                {
                    log.warn("流式调用异常结束：{}", error.getMessage());
                    emitter.completeWithError(error);
                },
                () ->
                {
                    // 打出推送次数与首段延迟：前端若看不到逐字效果，靠这行就能判断
                    // 是后端没有逐段推送，还是传输层把响应攒成了一整块
                    log.info("流式调用结束：推送 {} 次，首段 {} ms，总耗时 {} ms",
                            pushed.get(), firstPushAt.get(), System.currentTimeMillis() - start);
                    emitter.complete();
                }));
        return emitter;
    }

    /**
     * 图片理解
     */
    @Operation(summary = "图片理解", description = "图片传公网 URL 或 data:image/...;base64, 内联数据")
    @PreAuthorize("@ss.hasPermi('ai:playground:test')")
    @PostMapping("/vision")
    public AjaxResult vision(@RequestBody AiVisionRequest request)
    {
        return success(aiClient.vision(request));
    }

    /**
     * 文生图
     */
    @Operation(summary = "文生图", description = "内部完成提交、轮询、下载与转存，返回永久图片地址")
    @PreAuthorize("@ss.hasPermi('ai:playground:test')")
    @PostMapping("/image")
    public AjaxResult image(@RequestBody AiImageRequest request)
    {
        return success(aiClient.image(request));
    }

    /**
     * 文本向量
     */
    @Operation(summary = "文本向量", description = "返回向量与维度；完整向量很长，前端只展示前几个分量")
    @PreAuthorize("@ss.hasPermi('ai:playground:test')")
    @PostMapping("/embed")
    public AjaxResult embed(@RequestBody AiEmbedRequest request)
    {
        return success(aiClient.embed(request));
    }

    /**
     * 推送一个分片
     *
     * @param emitter      事件发射器
     * @param chunk        分片
     * @param subscription 上游订阅，推送失败时用它停掉
     */
    private void push(SseEmitter emitter, AiTextChunk chunk, AtomicReference<Disposable> subscription)
    {
        try
        {
            emitter.send(SseEmitter.event().data(chunk, MediaType.APPLICATION_JSON));
        }
        catch (Exception e)
        {
            log.debug("SSE 推送失败，停止本次流式调用：{}", e.getMessage());
            dispose(subscription);
            emitter.complete();
        }
    }

    /**
     * 取消上游订阅
     *
     * @param subscription 上游订阅
     */
    private void dispose(AtomicReference<Disposable> subscription)
    {
        Disposable disposable = subscription.get();
        if (disposable != null && !disposable.isDisposed())
        {
            disposable.dispose();
        }
    }
}
