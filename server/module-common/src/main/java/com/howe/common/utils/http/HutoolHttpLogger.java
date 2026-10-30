package com.howe.common.utils.http;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.GlobalInterceptor;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Hutool HTTP 全局日志
 *
 * <p>
 * 借助 hutool 的 {@link GlobalInterceptor} 给全项目的 {@code HttpRequest} 挂上请求/响应拦截器，
 * 业务代码不需要改任何一行就能拿到统一的出站调用日志：谁调的、打到哪、耗时多久、返回什么状态。
 * </p>
 *
 * <p>
 * 每条请求分两行输出：请求行 + 请求头/请求体，响应行 + 响应头。
 * </p>
 * <ul>
 *   <li>请求：方法 + URL（query 里的 token / secret / password 等敏感键自动掩码）+ 请求头 + 表单体；</li>
 *   <li>响应：状态码 + 耗时 + 内容长度 + 响应头；</li>
 *   <li>响应体不读——{@code executeAsync} 场景下 body 是未消费的流，读一下就会把数据取走，
 *       让调用方拿到空响应，所以这里只读响应头。</li>
 * </ul>
 *
 * <p>
 * 关闭方式有两种：配置 {@code howe.http-log.enabled=false} 整体关掉；
 * 或把 {@code logging.level.com.howe.common.utils.http.HutoolHttpLogger} 调到 warn 以上。
 * 日志量大时优先用后者，切换不需要重启也不需要改配置。
 * </p>
 *
 * @author howe
 */
@Slf4j
@Component
public class HutoolHttpLogger {

    /** 认定为敏感、需要掩码的 query 键关键字（小写包含匹配） */
    private static final String[] SENSITIVE_KEYWORDS = {
            "token", "secret", "password", "passwd", "key", "sign", "authorization"};

    /** 单条日志里 URL 的最大长度，超长截断，避免把整段 base64 签名刷进日志 */
    private static final int MAX_URL_LENGTH = 1000;

    /** 单条日志里请求体的最大长度，表单里可能塞 base64 图片 */
    private static final int MAX_BODY_LENGTH = 2000;

    /** 请求开始时间，响应拦截器据此算耗时；异步请求跨线程时取不到，按「未知」处理 */
    private final ThreadLocal<Long> startTime = new ThreadLocal<>();

    /** 是否启用全局日志 */
    @Value("${howe.http-log.enabled:true}")
    private boolean enabled;

    /**
     * 注册全局拦截器
     *
     * <p>
     * 只在应用启动时执行一次。拦截器注册进 {@link GlobalInterceptor} 单例后，
     * 之后创建的每个 {@code HttpRequest} 都会复制一份，因此对所有调用方生效。
     * </p>
     */
    @PostConstruct
    public void register() {
        if (!enabled) {
            log.info("Hutool HTTP 全局日志未启用（howe.http-log.enabled=false）");
            return;
        }
        GlobalInterceptor.INSTANCE
                .addRequestInterceptor(this::logRequest)
                .addResponseInterceptor(this::logResponse);
        log.info("Hutool HTTP 全局日志已启用");
    }

    /**
     * 记录出站请求
     *
     * @param request 即将发出的请求
     */
    private void logRequest(HttpRequest request) {
        startTime.set(System.currentTimeMillis());
        log.info("[HTTP] --> {} {}\n  请求头: {}\n  请求体: {}",
                request.getMethod(),
                maskUrl(request.getUrl()),
                formatHeaders(request.headers()),
                formatForm(request.form()));
    }

    /**
     * 记录下游响应
     *
     * <p>
     * 只读响应头，不触碰响应体，避免把流式响应读空。
     * </p>
     *
     * @param response 已收到的响应
     */
    private void logResponse(HttpResponse response) {
        Long start = startTime.get();
        startTime.remove();
        long cost = start == null ? -1L : System.currentTimeMillis() - start;
        long length = response.contentLength();
        log.info("[HTTP] <-- {} ({} ms, {} B)\n  响应头: {}",
                response.getStatus(),
                cost < 0 ? "?" : String.valueOf(cost),
                length < 0 ? "?" : String.valueOf(length),
                formatHeaders(response.headers()));
    }

    /**
     * 把 header 表格式化成单行文本
     *
     * <p>
     * hutool 的 headers 是 {@code Map<String, List<String>>}，值可能是多值（如 Set-Cookie），
     * 这里用 {@code [v1, v2]} 原样展开。Authorization / Cookie 等凭据类头做掩码，
     * 避免令牌落盘。
     * </p>
     *
     * @param headers 请求头或响应头
     * @return 形如 {@code {A=B, C=[D, E]}} 的单行文本；为空返回 {@code {}}
     */
    private String formatHeaders(Map<String, List<String>> headers) {
        if (headers == null || headers.isEmpty()) {
            return "{}";
        }
        return headers.entrySet().stream()
                // hutool 的 headers 里会混入 null 键（状态行等内部占位），跳过避免 NPE
                .filter(entry -> StrUtil.isNotBlank(entry.getKey()))
                .map(entry -> {
                    String name = entry.getKey();
                    List<String> values = entry.getValue();
                    String value = values == null || values.isEmpty() ? "" : String.join(", ", values);
                    if (isSensitiveHeader(name)) {
                        value = "***";
                    }
                    return name + "=" + value;
                })
                .collect(Collectors.joining(", ", "{", "}"));
    }

    /**
     * 格式化表单请求体，空表单不打印
     *
     * <p>
     * 直接 {@code String.valueOf(form)} 得到的是 hutool 内部 {@code TableMap{keys=[...], values=[...]}}
     * 结构，既不好读也绕过了掩码；这里按 {@code k=v} 展开，敏感键照样打码。
     * </p>
     *
     * @param form 表单参数表
     * @return 形如 {@code {q=1, token=***}} 的文本；空表单返回「无」
     */
    private String formatForm(Map<String, Object> form) {
        if (form == null || form.isEmpty()) {
            return "(无)";
        }
        String body = form.entrySet().stream()
                .map(entry -> {
                    String value = isSensitiveParam(entry.getKey()) ? "***" : String.valueOf(entry.getValue());
                    return entry.getKey() + "=" + value;
                })
                .collect(Collectors.joining(", ", "{", "}"));
        return StrUtil.maxLength(body, MAX_BODY_LENGTH);
    }

    /**
     * 判断请求头/响应头是否为凭据类
     *
     * @param name 头名称
     * @return true 表示值需要掩码
     */
    private boolean isSensitiveHeader(String name) {
        String lower = name.toLowerCase();
        return lower.contains("authorization") || lower.contains("cookie")
                || lower.contains("token") || lower.contains("key") || lower.contains("secret");
    }

    /**
     * 对 URL 里的敏感 query 参数做掩码并截断超长部分
     *
     * <p>
     * 微信 webhook key、AI 渠道密钥这类凭据经常直接拼在 URL 上，
     * 原样落日志等于把凭据写进文件，所以按关键字把值换成 {@code ***}。
     * </p>
     *
     * @param url 原始 URL
     * @return 可安全落盘的 URL
     */
    private String maskUrl(String url) {
        if (StrUtil.isBlank(url)) {
            return "-";
        }
        int index = url.indexOf('?');
        String masked = index < 0 ? url : url.substring(0, index) + "?" + Arrays.stream(url.substring(index + 1).split("&"))
                .map(param -> isSensitiveParam(param) ? StrUtil.subBefore(param, '=', false) + "=***" : param)
                .collect(Collectors.joining("&"));
        return StrUtil.maxLength(masked, MAX_URL_LENGTH);
    }

    /**
     * 判断 query 参数名是否为敏感项
     *
     * @param param 形如 {@code key=value} 的参数
     * @return true 表示值需要掩码
     */
    private boolean isSensitiveParam(String param) {
        String key = StrUtil.subBefore(param, '=', false).toLowerCase();
        return Arrays.stream(SENSITIVE_KEYWORDS).anyMatch(key::contains);
    }
}
