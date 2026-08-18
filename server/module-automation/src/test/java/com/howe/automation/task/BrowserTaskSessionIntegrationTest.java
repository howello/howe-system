package com.howe.automation.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import com.howe.automation.config.BrowserProperties;
import com.howe.automation.service.AutomationCredentialService;
import com.howe.automation.service.AutomationCredentialService.AutomationCredentials;
import com.howe.automation.session.BrowserSessionStore;
import com.microsoft.playwright.Page;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mockito.Mockito;

/**
 * 本地 mock 页面验证跨次执行的登录态恢复。
 */
class BrowserTaskSessionIntegrationTest
{
    @Test
    @EnabledIfSystemProperty(named = "automation.playwright.integration", matches = "true")
    void shouldReuseStorageStateOnSecondRun() throws Exception
    {
        HttpServer server = createServer();
        server.start();
        try
        {
            BrowserProperties properties = new BrowserProperties();
            BrowserSessionStore store = Mockito.mock(BrowserSessionStore.class);
            AutomationCredentialService credentialService = Mockito.mock(AutomationCredentialService.class);
            AutomationCredentials credentials = new AutomationCredentials("site-a", "user", "password");
            when(credentialService.getOptional("site-a")).thenReturn(credentials);

            AtomicReference<String> savedState = new AtomicReference<>();
            AtomicInteger reads = new AtomicInteger();
            when(store.get(any(), eq("site-a"))).thenAnswer(invocation -> {
                reads.incrementAndGet();
                return savedState.get();
            });
            doAnswer(invocation -> {
                savedState.set(invocation.getArgument(2));
                return null;
            }).when(store).save(any(), eq("site-a"), any());

            TestTask task = new TestTask(properties, store, credentialService,
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/");
            task.run("site-a");
            task.run("site-a");

            assertEquals(2, reads.get());
            assertEquals(1, task.loginCount.get());
            verify(store, Mockito.times(2)).save(any(), eq("site-a"), any());
        }
        finally
        {
            server.stop(0);
        }
    }

    private HttpServer createServer() throws Exception
    {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            String html = """
                    <!doctype html>
                    <html><body>
                    <button id='login' onclick=\"document.cookie='logged_in=true';location.reload()\">登录</button>
                    <span id='logged' hidden>已登录</span>
                    <button id='action'>业务操作</button>
                    <script>if (document.cookie.includes('logged_in=true')) document.querySelector('#logged').hidden=false</script>
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

    private static final class TestTask extends AbstractBrowserTask
    {
        private final String url;
        private final AtomicInteger loginCount = new AtomicInteger();

        private TestTask(BrowserProperties properties, BrowserSessionStore store,
                AutomationCredentialService credentialService, String url)
        {
            super(properties, store, credentialService);
            this.url = url;
        }

        private void run(String alias) throws Exception
        {
            executeBrowser(alias);
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
