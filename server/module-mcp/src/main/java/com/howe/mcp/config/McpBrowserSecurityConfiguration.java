package com.howe.mcp.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.howe.mcp.service.security.McpBrowserAdmissionFilter;
import com.howe.mcp.service.McpProtocolAdmission;
import com.howe.mcp.service.McpAuditService;
import com.howe.mcp.service.security.McpAuditFilter;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.savedrequest.NullRequestCache;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/** OAuth 浏览器登录与自助接口的独立安全链；授权服务器协议链将单独装配。 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "howe.mcp", name = "installed", havingValue = "true")
public class McpBrowserSecurityConfiguration {

    /** 在同一浏览器链内共享显式保存的身份和 CSRF 约定。 */
    @Bean
    public McpBrowserState mcpBrowserState() {
        return McpBrowserState.create();
    }

    /** 只匹配浏览器 API；不接受后台 JWT，不影响原后台链的准入策略。 */
    @Bean
    @Order(5)
    public SecurityFilterChain mcpBrowserSecurity(HttpSecurity http, McpEndpointSettings endpoints,
            McpBrowserState state, ObjectMapper mapper, McpProtocolAdmission admission, McpAuditService audit) throws Exception {
        var csrfHandler = new CsrfTokenRequestAttributeHandler();
        http.securityMatcher("/oauth2/login", "/oauth2/login/context", "/oauth2/self/**")
                .cors(cors -> cors.configurationSource(cors(endpoints)))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .securityContext(context -> context.securityContextRepository(state.contexts()).requireExplicitSave(true))
                .requestCache(cache -> cache.requestCache(new NullRequestCache()))
                .csrf(csrf -> csrf.csrfTokenRepository(state.csrf()).csrfTokenRequestHandler(csrfHandler))
                .authorizeHttpRequests(authorize -> authorize.requestMatchers("/oauth2/login", "/oauth2/login/context")
                        .permitAll().anyRequest().authenticated())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) -> error(response, mapper, 401, "login_required"))
                        .accessDeniedHandler((request, response, exception) -> error(response, mapper, 403, "request_rejected")))
                .formLogin(AbstractHttpConfigurer::disable).httpBasic(AbstractHttpConfigurer::disable).logout(AbstractHttpConfigurer::disable);
        http.addFilterAfter(new McpBrowserAdmissionFilter(admission, mapper), CorsFilter.class);
        http.addFilterBefore(new McpAuditFilter(audit), CorsFilter.class);
        return http.build();
    }

    /** 浏览器凭据只允许可信地址快照中的来源，预检不执行任何账号操作。 */
    private static UrlBasedCorsConfigurationSource cors(McpEndpointSettings endpoints) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.copyOf(endpoints.allowedOrigins()));
        config.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        config.setAllowedHeaders(List.of("Content-Type", "X-MCP-CSRF-TOKEN"));
        config.setAllowCredentials(true);
        config.setMaxAge(600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    /** 输出专属 JSON 错误，防止被旧后台 401 包装或重定向为 HTML。 */
    private static void error(HttpServletResponse response, ObjectMapper mapper, int status, String code) throws java.io.IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        mapper.writeValue(response.getOutputStream(), Map.of("error", code));
    }
}
