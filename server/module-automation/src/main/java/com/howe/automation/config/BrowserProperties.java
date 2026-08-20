package com.howe.automation.config;

import org.springframework.stereotype.Component;
import com.howe.common.constant.ConfigConstants;
import com.howe.common.utils.ConfigUtils;

/**
 * 浏览器连接配置。
 *
 * <p>不落本地 yml/env，全部从「系统管理 &gt; 参数设置」（sys_config）读取，
 * 由 {@link ConfigUtils} 走 Redis 缓存，在参数设置页改完即时生效、无需重启。
 * 具体任务仍通过构造器注入本组件，读取逻辑保持透明。</p>
 *
 * <p>开发环境默认在应用进程内启动本地 Chromium（mode=local）；生产环境在参数表把
 * mode 改成 remote 并配上 endpoint，即切换为 Playwright WebSocket 远程连接。</p>
 */
@Component
public class BrowserProperties
{
    /** local 或 remote。 */
    public String getMode()
    {
        return ConfigUtils.getString(ConfigConstants.AUTOMATION_BROWSER_MODE, "local");
    }

    /** Playwright server WebSocket 地址。 */
    public String getEndpoint()
    {
        return ConfigUtils.getString(ConfigConstants.AUTOMATION_BROWSER_ENDPOINT);
    }

    /** 是否以 headless 模式启动本地浏览器。 */
    public boolean isHeadless()
    {
        return ConfigUtils.getBoolean(ConfigConstants.AUTOMATION_BROWSER_HEADLESS, true);
    }

    /** 页面默认操作超时（毫秒）。 */
    public double getTimeoutMs()
    {
        return ConfigUtils.getDouble(ConfigConstants.AUTOMATION_BROWSER_TIMEOUT_MS, 10_000D);
    }

    /** 页面导航超时（毫秒）。 */
    public double getNavigationTimeoutMs()
    {
        return ConfigUtils.getDouble(ConfigConstants.AUTOMATION_BROWSER_NAVIGATION_TIMEOUT_MS, 30_000D);
    }
}