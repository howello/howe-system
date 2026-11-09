package com.howe.web.mcp;

import com.howe.framework.security.filter.JwtAuthenticationTokenFilter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.CorsFilter;

/** 安装 MCP 后，旧过滤器继续由旧安全链调用，避免 Servlet 全局注册越过新链边界。 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "howe.mcp", name = "installed", havingValue = "true")
public class McpLegacyFilterIsolation {

    /** 旧 JWT 不在 OAuth 浏览器链外额外执行，原 SecurityConfig 中的注册保留。 */
    @Bean
    public FilterRegistrationBean<JwtAuthenticationTokenFilter> mcpIsolateLegacyJwt(JwtAuthenticationTokenFilter filter) {
        FilterRegistrationBean<JwtAuthenticationTokenFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    /** 旧通配 CORS 仍用于旧安全链，不先行污染 OAuth 的精确跨域判定。 */
    @Bean
    public FilterRegistrationBean<CorsFilter> mcpIsolateLegacyCors(CorsFilter filter) {
        FilterRegistrationBean<CorsFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
