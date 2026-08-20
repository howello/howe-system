package com.howe.automation.task;

import com.howe.automation.service.AutomationCredentialService;
import com.howe.automation.service.AutomationCredentialService.AutomationCredentials;
import com.howe.common.constant.ConfigConstants;
import com.howe.common.task.TaskLogContext;
import com.howe.common.task.TaskLogContext.TaskStep;
import com.howe.common.utils.ConfigUtils;
import com.microsoft.playwright.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 浏览器自动化任务模板。
 *
 * <p>具体任务只需实现登录态判断、登录流程和业务页面操作。浏览器通过
 * {@code launchPersistentContext} 连接全局持久 profile（UserDataDir），登录态保存在磁盘，
 * 任务结束关闭浏览器但目录保留，下次执行自动复用，天然支持同一 context 内多标签页共享登录态。
 * 步骤日志与资源释放由模板统一处理。</p>
 */
@Slf4j
public abstract class AbstractBrowserTask {
    private final AutomationCredentialService credentialService;

    protected AbstractBrowserTask(AutomationCredentialService credentialService) {
        this.credentialService = credentialService;
    }

    /**
     * 执行一次浏览器任务。
     *
     * @param credentialAlias 凭据别名，不能是实际账号密码
     * @throws Exception 页面操作或登录失败
     */
    protected final void executeBrowser(String credentialAlias) throws Exception {
        AutomationCredentials credentials;
        try (TaskStep step = TaskLogContext.startStep("读取自动化凭据")) {
            try {
                credentials = credentialService.getOptional(credentialAlias);
                if (credentials == null) {
                    step.skipped("未配置凭据，优先复用全局 profile 登录态");
                } else {
                    step.success("已读取凭据别名：" + credentials.alias());
                }
            } catch (RuntimeException e) {
                step.needsAuth("自动化凭据不可用：" + credentialAlias);
                throw e;
            }
        }

        Path profileDir = resolveProfileDir();

        try (Playwright playwright = Playwright.create()) {
            try (TaskStep step = TaskLogContext.startStep("连接浏览器")) {
                step.success("浏览器连接准备完成");
            }

            try (BrowserContext context = createContext(playwright, profileDir)) {
                double timeout = ConfigUtils.getDouble(ConfigConstants.AUTOMATION_BROWSER_TIMEOUT_MS, 10_000D);
                double navigationTimeout = ConfigUtils.getDouble(ConfigConstants.AUTOMATION_BROWSER_NAVIGATION_TIMEOUT_MS, 30_000D);
                Page page = context.newPage();
                page.setDefaultTimeout(timeout);
                page.setDefaultNavigationTimeout(navigationTimeout);

                boolean authenticated;
                try (TaskStep step = TaskLogContext.startStep("检查登录态")) {
                    authenticated = isAuthenticated(page);
                    if (authenticated) {
                        step.success("全局 profile 登录态有效");
                    } else {
                        step.skipped("全局 profile 未登录或已失效");
                    }
                }

                if (!authenticated) {
                    if (credentials == null) {
                        try (TaskStep step = TaskLogContext.startStep("自动登录")) {
                            step.needsAuth("未配置自动登录凭据");
                        }
                        throw new NeedsAuthenticationException("未配置自动登录凭据：" + credentialAlias);
                    }

                    try (TaskStep step = TaskLogContext.startStep("自动登录")) {
                        try {
                            login(page, credentials);
                            if (!isAuthenticated(page)) {
                                step.needsAuth("自动登录后仍未通过登录态检查");
                                throw new IllegalStateException("自动登录失败");
                            }
                            step.success("自动登录成功");
                        } catch (Exception e) {
                            step.fail("自动登录失败", e);
                            throw e;
                        }
                    }
                }

                try (TaskStep step = TaskLogContext.startStep("执行页面流程")) {
                    try {
                        runWorkflow(page, credentials);
                        step.success("页面流程执行完成");
                    } catch (Exception e) {
                        step.fail("页面流程失败", e);
                        throw e;
                    }
                }
            }
        }
    }

    /**
     * 解析全局持久 profile 目录并确保存在。
     *
     * @return profile 目录路径
     */
    protected Path resolveProfileDir() {
        String dir = ConfigUtils.getRequired(ConfigConstants.AUTOMATION_BROWSER_PROFILE_DIR, "自动化浏览器 profile 目录");
        Path path = Paths.get(dir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(path);
        } catch (Exception e) {
            throw new IllegalStateException("无法创建浏览器 profile 目录：" + path, e);
        }
        return path;
    }

    /**
     * 判断当前页面是否已经登录。
     *
     * @param page 当前页面
     * @return true 表示登录态有效
     * @throws Exception 页面检查失败
     */
    protected abstract boolean isAuthenticated(Page page) throws Exception;

    /**
     * 执行登录流程。已有 session 有效时不会调用。
     *
     * @param page 当前页面
     * @param credentials 凭据
     * @throws Exception 登录失败
     */
    protected abstract void login(Page page, AutomationCredentials credentials) throws Exception;

    /**
     * 执行具体业务流程。若已有 session 有效但未配置凭据，credentials 为 null。
     *
     * @param page 当前页面
     * @param credentials 凭据或 null
     * @throws Exception 业务流程失败
     */
    protected abstract void runWorkflow(Page page, AutomationCredentials credentials) throws Exception;

    /**
     * 创建连接到全局持久 profile 的浏览器上下文。
     *
     * @param playwright Playwright 实例
     * @param profileDir 全局 profile 目录
     * @return 持久上下文，close 时关闭浏览器但保留 profile 目录
     */
    private BrowserContext createContext(Playwright playwright, Path profileDir) {
        boolean isHeadless = ConfigUtils.getBoolean(ConfigConstants.AUTOMATION_BROWSER_HEADLESS, true);
        BrowserType.LaunchPersistentContextOptions options = new BrowserType.LaunchPersistentContextOptions()
            .setHeadless(isHeadless);
        return playwright.chromium().launchPersistentContext(profileDir, options);
    }

    private static final class NeedsAuthenticationException extends IllegalStateException {
        private NeedsAuthenticationException(String message) {
            super(message);
        }
    }
}
