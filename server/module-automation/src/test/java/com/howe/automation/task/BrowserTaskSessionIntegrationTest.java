package com.howe.automation.task;

import com.howe.automation.service.AutomationCredentialService;
import com.howe.automation.service.AutomationCredentialService.AutomationCredentials;
import com.microsoft.playwright.Page;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mockito.Mockito;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/**
 * 本地 mock 页面验证跨次执行的全局持久 profile 登录态恢复。
 *
 * <p>需要对象存储外的本地 Chromium，显式传入
 * {@code -Dautomation.playwright.integration=true} 才运行。</p>
 */
class BrowserTaskSessionIntegrationTest
{
    @Test
    @EnabledIfSystemProperty(named = "automation.playwright.integration", matches = "true")
    void shouldReusePersistentProfileOnSecondRun() throws Exception
    {
        HttpServer server = createServer();
        server.start();
        Path profileDir = Files.createTempDirectory("howe-profile-test");
        try
        {
            AutomationCredentialService credentialService = Mockito.mock(AutomationCredentialService.class);
            AutomationCredentials credentials = new AutomationCredentials("site-a", "user", "password");
            when(credentialService.getOptional("site-a")).thenReturn(credentials);

            AtomicInteger loginCount = new AtomicInteger();
            TestTask task = new TestTask(credentialService,
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/",
                    profileDir, loginCount);

            task.run("site-a");
            task.run("site-a");

            // 第二次执行复用同一 profile，不再重复登录
            assertEquals(1, loginCount.get());
        }
        finally
        {
            server.stop(0);
            deleteRecursively(profileDir);
        }
    }

    @Test
    @EnabledIfSystemProperty(named = "automation.playwright.integration", matches = "true")
    void shouldFailWithNeedsAuthWhenNoCredentialAndEmptyProfile() throws Exception
    {
        HttpServer server = createServer();
        server.start();
        Path profileDir = Files.createTempDirectory("howe-profile-test");
        try
        {
            AutomationCredentialService credentialService = Mockito.mock(AutomationCredentialService.class);
            when(credentialService.getOptional("site-a")).thenReturn(null);

            TestTask task = new TestTask(credentialService,
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/",
                    profileDir, new AtomicInteger());

            // 空 profile 且无凭据：应抛异常（NEEDS_AUTH），且不调用 login
            assertThrows(RuntimeException.class, () -> task.run("site-a"));
            assertEquals(0, task.loginCount.get());
        }
        finally
        {
            server.stop(0);
            deleteRecursively(profileDir);
        }
    }

    private HttpServer createServer() throws Exception
    {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            String html = """
                    <!doctype html>
                    <html><body>
                    <button id='login' onclick="localStorage.setItem('logged_in','true');location.reload()">登录</button>
                    <span id='logged' hidden>已登录</span>
                    <button id='action'>业务操作</button>
                    <script>if (localStorage.getItem('logged_in')==='true') document.querySelector('#logged').hidden=false</script>
                    </body></html>
                    """;
            byte[] body = html.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream output = exchange.getResponseBody())
            {
                output.write(body);
            }
        });
        return server;
    }

    private void deleteRecursively(Path root) throws Exception
    {
        if (root == null || !Files.exists(root))
        {
            return;
        }
        try (Stream<Path> paths = Files.walk(root))
        {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try
                {
                    Files.deleteIfExists(path);
                }
                catch (Exception ignored)
                {
                }
            });
        }
    }

    private static final class TestTask extends AbstractBrowserTask
    {
        private final String url;
        private final Path profileDir;
        private final AtomicInteger loginCount;

        private TestTask(AutomationCredentialService credentialService, String url,
                Path profileDir, AtomicInteger loginCount)
        {
            super(credentialService);
            this.url = url;
            this.profileDir = profileDir;
            this.loginCount = loginCount;
        }

        private void run(String alias) throws Exception
        {
            executeBrowser(alias);
        }

        @Override
        protected Path resolveProfileDir()
        {
            return profileDir;
        }

        @Override
        protected boolean isAuthenticated(Page page)
        {
            page.navigate(url);
            return page.locator("#logged").isVisible();
        }

        @Override
        protected void login(Page page, AutomationCredentials credentials)
        {
            loginCount.incrementAndGet();
            page.locator("#login").click();
        }

        @Override
        protected void runWorkflow(Page page, AutomationCredentials credentials)
        {
            page.locator("#action").click();
        }
    }
}
