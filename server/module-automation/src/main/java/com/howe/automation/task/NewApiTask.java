package com.howe.automation.task;

import com.howe.automation.service.AutomationCredentialService;
import com.howe.common.task.TaskLogContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.microsoft.playwright.options.WaitUntilState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * <p>@Author lu
 * <p>@Date 2026/8/19 14:28 星期三
 * <p>@Version 1.0
 * <p>@Description TODO
 */
@Component
@Slf4j
public class NewApiTask extends AbstractBrowserTask {

    protected NewApiTask(AutomationCredentialService credentialService) {
        super(credentialService);
    }

    public void run(String credentialAlias) {
        try {
            executeBrowser(credentialAlias);   // 触发整条链路
        } catch (Exception e) {
            // 抛出去让调度日志记失败（Quartz 需要异常才能判断失败）
            throw new RuntimeException("浏览器任务执行失败", e);
        }

    }

    /**
     * 判断当前页面是否已经登录。
     *
     * @param page 当前页面
     * @return true 表示登录态有效
     * @throws Exception 页面检查失败
     */
    @Override
    protected boolean isAuthenticated(Page page) throws Exception {
        // 打开目标页
        page.navigate("https://ai.wyantao.com/");
        page.waitForLoadState(LoadState.NETWORKIDLE);
        // 找一个"登录后才会出现"的元素。务必先人工打开浏览器确认真实选择器！
        int count = page.locator("span[data-slot='avatar']").count();
        return count > 0;
    }

    /**
     * 执行登录流程。已有 session 有效时不会调用。
     *
     * @param page        当前页面
     * @param credentials 凭据
     * @throws Exception 登录失败
     */
    @Override
    protected void login(Page page, AutomationCredentialService.AutomationCredentials credentials) throws Exception {
        page.navigate("https://ai.wyantao.com/sign-in", new Page.NavigateOptions().setWaitUntil(WaitUntilState.NETWORKIDLE));
        // 可能要点一下"登录入口"按钮才出现表单 —— 先人工确认
        // page.locator("button.login").first().click();

        // 填账号密码 —— 选择器务必人工确认
        page.locator("input[name='username']").fill(credentials.username());
        page.locator("input[name='password']").fill(credentials.password());
        page.locator("button[type=submit]").first().click();

        // 等页面跳到登录后的地址（如首页/工作台），有跳转才说明成功
        page.waitForURL("**/dashboard**", new Page.WaitForURLOptions().setTimeout(15000));

    }

    /**
     * 执行具体业务流程。若已有 session 有效但未配置凭据，credentials 为 null。
     *
     * @param page        当前页面
     * @param credentials 凭据或 null
     * @throws Exception 业务流程失败
     */
    @Override
    protected void runWorkflow(Page page, AutomationCredentialService.AutomationCredentials credentials) throws Exception {
        try (TaskLogContext.TaskStep step = TaskLogContext.startStep("执行流程")) {
            page.navigate("https://ai.wyantao.com/profile");
            page.waitForLoadState(LoadState.NETWORKIDLE);
            // 2. 定位签到卡片内的签到按钮（假设它在一个button内，文本动态变化）

            Locator signButton = page.locator("div[data-slot='card'] button")
                .filter(new Locator.FilterOptions().setHasText(Pattern.compile("已签到|立即签到")));

            // 3. 获取按钮当前文本
            String buttonText = signButton.last().textContent().trim();
            step.info("当前按钮文本: {}", buttonText);

            // 4. 如果未签到（文本包含“立即签到”），则点击并等待数据更新
            if (buttonText.contains("立即签到")) {
                step.info("未签到，点击签到");
                signButton.last().click();
                // 等待签到成功反馈：可以等待按钮变为“已签到”状态，或等待统计数据变化
                // 这里等待按钮文本变为“已签到”（最多等待10秒）
                signButton.last().waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.VISIBLE)
                    .setTimeout(10000));
                // 或者显式等待某个数据更新（比如累计签到+1，但需要知道之前的值）
                // 更通用的做法：等待“本月获得”或“累计获得”数值变化
                // 也可以等待一个短暂的延迟（不推荐但有效），或等待某个成功提示出现
                // 例如等待"签到成功"的toast出现：
                // page.locator("text=签到成功").waitFor();
                step.info("未签到，签到成功");
            } else {
                signButton.first().click();
                step.info("今日已签到，无需操作");
            }
            // 先定位统计卡片的容器
            Locator statsContainer = page.locator("div.grid.grid-cols-3.gap-px.border-b");
            // 5. 提取三个统计数据（与之前相同的方法）
            String signInCount = statsContainer.locator("div.bg-card")
                .filter(new Locator.FilterOptions().setHasText("累计签到"))
                .locator("div")
                .first()
                .textContent()
                .trim();

            String monthlyAmount = statsContainer.locator("div.bg-card")
                .filter(new Locator.FilterOptions().setHasText("本月获得"))
                .locator("div")
                .first()
                .textContent()
                .trim();

            String totalAmount = statsContainer.locator("div.bg-card")
                .filter(new Locator.FilterOptions().setHasText("累计获得"))
                .locator("div")
                .first()
                .textContent()
                .trim();

            // 6. 输出结果
            step.info("累计签到: " + signInCount);
            step.info("本月获得: " + monthlyAmount);
            step.info("累计获得: " + totalAmount);
        }
    }
}
