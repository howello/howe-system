package com.howe.automation.task;

import com.howe.automation.config.BrowserProperties;
import com.howe.automation.service.AutomationCredentialService;
import com.howe.automation.service.AutomationCredentialService.AutomationCredentials;
import com.howe.automation.session.BrowserSessionStore;
import com.howe.common.task.TaskLogContext;
import com.howe.common.task.TaskLogContext.TaskStep;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

/**
 * 浏览器自动化任务模板。
 *
 * <p>具体任务只需实现登录态判断、登录流程和业务页面操作，浏览器连接、上下文、
 * Redis session、步骤日志和资源释放由模板统一处理。</p>
 */
@Slf4j
public abstract class AbstractBrowserTask
{
    private final BrowserProperties browserProperties;
    private final BrowserSessionStore sessionStore;
    private final AutomationCredentialService credentialService;

    protected AbstractBrowserTask(BrowserProperties browserProperties,
            BrowserSessionStore sessionStore, AutomationCredentialService credentialService)
    {
        this.browserProperties = browserProperties;
        this.sessionStore = sessionStore;
        this.credentialService = credentialService;
    }

    /**
     * 执行一次浏览器任务。
     *
     * @param credentialAlias 凭据别名，不能是实际账号密码
     * @throws Exception 页面操作或登录失败
     */
    protected final void executeBrowser(String credentialAlias) throws Exception
    {
        AutomationCredentials credentials;
        try (TaskStep step = TaskLogContext.startStep("读取自动化凭据"))
        {
            try
            {
                credentials = credentialService.getOptional(credentialAlias);
                if (credentials == null)
                {
                    step.skipped("未配置凭据，优先复用已有登录态");
                }
                else
                {
                    step.success("已读取凭据别名：" + credentials.alias());
                }
            }
            catch (RuntimeException e)
            {
                step.needsAuth("自动化凭据不可用：" + credentialAlias);
                throw e;
            }
        }

        try (Playwright playwright = Playwright.create())
        {
            try (TaskStep step = TaskLogContext.startStep("连接浏览器"))
            {
                step.success("浏览器连接准备完成");
            }

            try (Browser browser = connectBrowser(playwright))
            {
                String sessionState = sessionStore.get(taskKey(), credentialAlias);
                BrowserContext context = createContext(browser, sessionState, credentialAlias);
                try (context)
                {
                    Page page = context.newPage();
                    page.setDefaultTimeout(browserProperties.getTimeoutMs());
                    page.setDefaultNavigationTimeout(browserProperties.getNavigationTimeoutMs());

                    boolean authenticated;
                    try (TaskStep step = TaskLogContext.startStep("检查登录态"))
                    {
                        authenticated = isAuthenticated(page);
                        if (authenticated)
                        {
                            step.success(StringUtils.hasText(sessionState)
                                    ? "Redis 登录态有效" : "页面无需重新登录");
                            sessionStore.refresh(taskKey(), credentialAlias);
                        }
                        else
                        {
                            step.skipped("登录态不存在或已失效");
                        }
                    }

                    if (!authenticated)
                    {
                        sessionStore.delete(taskKey(), credentialAlias);
                        if (credentials == null)
                        {
                            try (TaskStep step = TaskLogContext.startStep("自动登录"))
                            {
                                step.needsAuth("未配置自动登录凭据");
                            }
                            throw new NeedsAuthenticationException("未配置自动登录凭据：" + credentialAlias);
                        }

                        try (TaskStep step = TaskLogContext.startStep("自动登录"))
                        {
                            try
                            {
                                login(page, credentials);
                                if (!isAuthenticated(page))
                                {
                                    step.needsAuth("自动登录后仍未通过登录态检查");
                                    throw new IllegalStateException("自动登录失败");
                                }
                                step.success("自动登录成功");
                            }
                            catch (Exception e)
                            {
                                step.fail("自动登录失败", e);
                                throw e;
                            }
                        }
                    }

                    try (TaskStep step = TaskLogContext.startStep("执行页面流程"))
                    {
                        try
                        {
                            runWorkflow(page, credentials);
                            step.success("页面流程执行完成");
                        }
                        catch (Exception e)
                        {
                            step.fail("页面流程失败", e);
                            throw e;
                        }
                    }

                    try (TaskStep step = TaskLogContext.startStep("保存登录态"))
                    {
                        sessionStore.save(taskKey(), credentialAlias, context.storageState());
                        step.success("登录态已保存");
                    }
                }
            }
        }
    }

    /**
     * 任务稳定标识，用于隔离不同 Task 的 Redis session。
     */
    protected String taskKey()
    {
        return getClass().getName();
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

    private BrowserContext createContext(Browser browser, String sessionState, String credentialAlias)
    {
        Browser.NewContextOptions contextOptions = new Browser.NewContextOptions();
        if (StringUtils.hasText(sessionState))
        {
            contextOptions.setStorageState(sessionState);
            try
            {
                return browser.newContext(contextOptions);
            }
            catch (RuntimeException e)
            {
                log.warn("Redis 登录态无法加载，将删除并创建新会话：{}", credentialAlias);
                sessionStore.delete(taskKey(), credentialAlias);
            }
        }
        return browser.newContext(new Browser.NewContextOptions());
    }

    private Browser connectBrowser(Playwright playwright)
    {
        if ("remote".equalsIgnoreCase(browserProperties.getMode()))
        {
            if (!StringUtils.hasText(browserProperties.getEndpoint()))
            {
                throw new IllegalStateException("远程浏览器模式未配置 automation.browser.endpoint");
            }
            return playwright.chromium().connect(browserProperties.getEndpoint());
        }
        BrowserType.LaunchOptions options = new BrowserType.LaunchOptions()
                .setHeadless(browserProperties.isHeadless());
        return playwright.chromium().launch(options);
    }

    private static final class NeedsAuthenticationException extends IllegalStateException
    {
        private NeedsAuthenticationException(String message)
        {
            super(message);
        }
    }
}
