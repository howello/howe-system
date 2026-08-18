package com.howe.automation.task;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * 本地 mock 页面浏览器集成验证。
 *
 * <p>需要在已安装 Chromium 的环境中显式传入
 * {@code -Dautomation.playwright.integration=true} 才运行，默认单元测试不依赖本机浏览器。</p>
 */
class BrowserMockPageTest
{
    @Test
    @EnabledIfSystemProperty(named = "automation.playwright.integration", matches = "true")
    void shouldClickMockLoginPage() throws Exception
    {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            String html = """
                    <!doctype html>
                    <html><body>
                    <button id='login' onclick=\"document.cookie='logged_in=true';document.body.dataset.loggedIn='true';document.querySelector('#result').textContent='已登录'\">登录</button>
                    <span id='result'>未登录</span>
                    <script>document.cookie.includes('logged_in=true') && (document.querySelector('#result').textContent='已登录')</script>
                    </body></html>
                    """;
            byte[] body = html.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream output = exchange.getResponseBody())
            {
                output.write(body);
            }
        });
        server.start();

        try (Playwright playwright = Playwright.create();
                Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true)))
        {
            Page page = browser.newPage();
            page.navigate("http://127.0.0.1:" + server.getAddress().getPort() + "/");
            page.locator("#login").click();
            assertEquals("已登录", page.locator("#result").textContent());
        }
        finally
        {
            server.stop(0);
        }
    }
}
