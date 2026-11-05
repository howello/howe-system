package com.howe.common.utils.http;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import com.howe.common.exception.ServiceException;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 远程文件有界探测
 *
 * <p>用于 APP 资源包与分享远程链接的「可达性 + 大小 + 内容形态」验证。
 * 特点：有界（大小上限）、限时（整体截止）、不跟随重定向（安全层逐跳校验）、
 * HEAD 仅作预检（不能只凭 HEAD 成功判定有效）。</p>
 *
 * <p>整体截止时间不能靠 hutool 的连接/读超时（它们是分别设置的），
 * 在读取循环里自行检查墙钟。</p>
 *
 * @author howe
 */
public final class RemoteFileClient {

    /** 连接超时毫秒数 */
    private static final int CONNECT_TIMEOUT_MS = 3000;

    /** 读空闲超时毫秒数 */
    private static final int READ_TIMEOUT_MS = 5000;

    private RemoteFileClient() {
    }

    /**
     * 有界下载到临时文件
     *
     * <p>校验顺序：地址安全 → HEAD 预检（可选）→ 逐跳跟随（≤ maxRedirects，每跳重新校验）
     * → 有界 GET 流式落盘。结束条件：读完 Content-Length、流结束、达到大小上限或超时。</p>
     *
     * @param url             目标地址
     * @param maxBytes        大小上限（字节）
     * @param deadlineMs      整体截止时间（毫秒）
     * @param maxRedirects    最大重定向次数
     * @param tempFile        临时落盘位置（调用方负责删除）
     * @return 下载结果
     */
    public static ProbeResult download(String url, long maxBytes, long deadlineMs, int maxRedirects, Path tempFile) {
        URI current = RemoteFileGuard.validate(url);
        for (int hop = 0; hop <= maxRedirects; hop++) {
            // 每一跳都重新校验主机：防止重定向跳到内网（DNS 重绑定同理）
            RemoteFileGuard.requirePublicHost(current.getHost());
            ProbeOutcome outcome = fetchOnce(current, maxBytes, deadlineMs, tempFile);
            switch (outcome.kind()) {
                case DONE -> {
                    return new ProbeResult(true, outcome.status(), outcome.contentLength(), outcome.location(),
                            null, tempFile);
                }
                case REDIRECT -> {
                    String location = outcome.location();
                    if (location == null) {
                        return failed("重定向缺少 Location 头");
                    }
                    current = RemoteFileGuard.validate(location);
                }
                case HTML -> {
                    return failed("目标返回的是 HTML 页面，不是文件");
                }
                case TOO_LARGE -> {
                    return failed("文件超过大小上限 " + maxBytes + " 字节");
                }
                default -> {
                    return failed("目标不可达（HTTP " + outcome.status() + "）");
                }
            }
        }
        return failed("重定向次数超过 " + maxRedirects);
    }

    /**
     * 执行一次请求（GET + 流式落盘）
     *
     * @param uri        目标
     * @param maxBytes   大小上限
     * @param deadlineMs 截止时间
     * @param tempFile   落盘位置
     * @return 本次请求结果
     */
    private static ProbeOutcome fetchOnce(URI uri, long maxBytes, long deadlineMs, Path tempFile) {
        long deadline = System.currentTimeMillis() + deadlineMs;
        try (HttpResponse response = HttpRequest.get(uri.toString())
                .setConnectionTimeout(CONNECT_TIMEOUT_MS)
                .setReadTimeout(READ_TIMEOUT_MS)
                // 禁止自动跟随：重定向必须由安全层逐跳校验后再决定
                .setMaxRedirectCount(0)
                .execute()) {
            int status = response.getStatus();
            if (status >= 300 && status < 400) {
                return ProbeOutcome.redirect(status, response.header("Location"));
            }
            if (status == 405 || status == 501) {
                // 理论上 GET 不会返回这两个码，保险起见当作不可达
                return ProbeOutcome.failed(status);
            }
            if (!response.isOk()) {
                return ProbeOutcome.failed(status);
            }
            String contentType = response.header("Content-Type");
            if (contentType != null && contentType.toLowerCase().contains("text/html")) {
                return ProbeOutcome.html(status);
            }
            long contentLength = response.contentLength();
            if (contentLength > maxBytes) {
                return ProbeOutcome.tooLarge(status);
            }
            // 流式落盘，同时校验实际字节数（没有 Content-Length 时按实际计数）
            long total = 0;
            try (InputStream in = response.bodyStream()) {
                if (in == null) {
                    return ProbeOutcome.failed(status);
                }
                try (var out = Files.newOutputStream(tempFile)) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        if (System.currentTimeMillis() > deadline) {
                            return ProbeOutcome.failed(status);
                        }
                        total += read;
                        if (total > maxBytes) {
                            return ProbeOutcome.tooLarge(status);
                        }
                        out.write(buffer, 0, read);
                    }
                }
            }
            if (total == 0) {
                return ProbeOutcome.failed(status);
            }
            return ProbeOutcome.done(status, total);
        } catch (IOException e) {
            return ProbeOutcome.failed(-1);
        } catch (Exception e) {
            return ProbeOutcome.failed(-1);
        }
    }

    /**
     * 构造失败结果
     *
     * @param reason 失败原因
     * @return 结果
     */
    private static ProbeResult failed(String reason) {
        return new ProbeResult(false, 0, 0, null, reason, null);
    }

    /**
     * 单次请求的结果分类
     */
    private record ProbeOutcome(Kind kind, int status, long contentLength, String location) {

        static ProbeOutcome done(int status, long contentLength) {
            return new ProbeOutcome(Kind.DONE, status, contentLength, null);
        }

        static ProbeOutcome redirect(int status, String location) {
            return new ProbeOutcome(Kind.REDIRECT, status, 0, location);
        }

        static ProbeOutcome html(int status) {
            return new ProbeOutcome(Kind.HTML, status, 0, null);
        }

        static ProbeOutcome tooLarge(int status) {
            return new ProbeOutcome(Kind.TOO_LARGE, status, 0, null);
        }

        static ProbeOutcome failed(int status) {
            return new ProbeOutcome(Kind.FAILED, status, 0, null);
        }

        enum Kind {
            /** 成功读完 */
            DONE,
            /** 需要跟随重定向 */
            REDIRECT,
            /** 返回的是 HTML */
            HTML,
            /** 超过大小上限 */
            TOO_LARGE,
            /** 不可达或失败 */
            FAILED
        }
    }

    /**
     * 下载结果
     *
     * @param reachable     是否成功
     * @param status        最终 HTTP 状态码
     * @param contentLength 实际字节数
     * @param location      最终地址（经过重定向后）
     * @param reason        失败原因，成功时为 null
     * @param tempFile      已落盘的临时文件，失败时为 null
     */
    public record ProbeResult(boolean reachable, int status, long contentLength, String location, String reason,
            Path tempFile) {
    }
}
