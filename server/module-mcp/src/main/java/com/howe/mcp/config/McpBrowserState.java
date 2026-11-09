package com.howe.mcp.config;

import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;

/** OAuth 专属上下文与 CSRF 存储，不作为全局 SecurityContextRepository 自动接管旧入口。 */
public record McpBrowserState(HttpSessionSecurityContextRepository contexts, HttpSessionCsrfTokenRepository csrf) {

    /** 每个安装实例共享同一浏览器协议约定，实际数据由作用于 OAuth 路径的 Session 过滤器保存。 */
    public static McpBrowserState create() {
        var contexts = new HttpSessionSecurityContextRepository();
        contexts.setDisableUrlRewriting(true);
        var csrf = new HttpSessionCsrfTokenRepository();
        csrf.setHeaderName("X-MCP-CSRF-TOKEN");
        return new McpBrowserState(contexts, csrf);
    }
}
