package com.howe.automation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import lombok.Data;

/**
 * 浏览器连接配置。
 *
 * <p>开发环境默认在应用进程内启动本地 Chromium；生产环境通过环境变量切换为
 * Playwright WebSocket 远程连接。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "automation.browser")
public class BrowserProperties
{
    /** local 或 remote。 */
    private String mode = "local";

    /** Playwright server WebSocket 地址。 */
    private String endpoint;

    /** 是否以 headless 模式启动本地浏览器。 */
    private boolean headless = true;

    /** 页面默认操作超时。 */
    private double timeoutMs = 10_000D;

    /** 页面导航超时。 */
    private double navigationTimeoutMs = 30_000D;
}
