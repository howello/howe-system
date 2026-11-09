package com.howe.mcp.config;

import com.howe.mcp.service.impl.McpBearerAuthenticationServiceImpl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.howe.common.config.ConfigProvider;
import com.howe.mcp.service.protocol.McpHttpSessionRegistry;
import com.howe.mcp.service.McpBearerAuthenticationService;
import com.howe.mcp.service.security.McpResourceFilter;
import com.howe.mcp.service.McpGrantService;
import com.howe.mcp.service.McpJdbcAuthorizationService;
import com.howe.mcp.service.McpProtocolAdmission;
import com.howe.mcp.service.McpAuditService;
import com.howe.mcp.service.security.McpAuditFilter;
import com.howe.mcp.service.McpCimdClientService;
import com.howe.mcp.service.spi.McpAccountBridge;
import com.howe.mcp.tool.McpToolRegistry;
import jakarta.servlet.DispatcherType;
import java.time.Clock;
import java.time.Duration;
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
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.context.NullSecurityContextRepository;
import org.springframework.security.web.savedrequest.NullRequestCache;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.function.RequestPredicates;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerResponse;

/** 显式安装才开放 MCP 资源；仅 Bearer 认证，不使用后台 JWT 或 OAuth 浏览器 Cookie。 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "howe.mcp", name = "installed", havingValue = "true")
public class McpResourceConfiguration {

    /** 复用同一套持久化授权与账号状态，不连接外部 introspection 服务。 */
    @Bean
    public McpBearerAuthenticationService mcpBearerAuthentication(McpJdbcAuthorizationService authorizations,
            JdbcRegisteredClientRepository clients, McpGrantService grants, McpAccountBridge accounts,
            ConfigProvider config, McpEndpointSettings endpoints, McpCimdClientService cimd) {
        return new McpBearerAuthenticationServiceImpl(authorizations, clients, grants, accounts, config, endpoints, Clock.systemUTC(), cimd);
    }

    /** 首期容量有界：全局 256、每用户 8、空闲 15 分钟；重启后客户端重新初始化。 */
    @Bean(destroyMethod = "close")
    public McpHttpSessionRegistry mcpHttpSessions(McpToolRegistry tools, McpBearerAuthenticationService authentication) {
        return new McpHttpSessionRegistry(tools, authentication, Clock.systemUTC(), 256, 8, Duration.ofMinutes(15));
    }

    /** 公开的是 SDK 协议入口，不暴露业务 Controller 或隐式工具扫描。 */
    @Bean
    public RouterFunction<ServerResponse> mcpResourceRoutes(McpHttpSessionRegistry sessions) {
        return RouterFunctions.route(RequestPredicates.path("/mcp"), sessions::handle);
    }

    /** 可信 Origin 与无 Origin 的 CLI 可访问，每一个请求包括 GET/DELETE 都必须重新验证访问令牌。 */
    @Bean
    @Order(3)
    public SecurityFilterChain mcpResourceSecurity(HttpSecurity http, McpEndpointSettings endpoints,
            McpBearerAuthenticationService authentication, ObjectMapper mapper, McpProtocolAdmission admission,
            McpAuditService audit) throws Exception {
        var cors = new CorsConfiguration();
        cors.setAllowedOrigins(List.copyOf(endpoints.allowedOrigins()));
        cors.setAllowCredentials(false);
        cors.setAllowedMethods(List.of("GET", "POST", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "Mcp-Session-Id", "MCP-Protocol-Version", "Last-Event-ID"));
        cors.setExposedHeaders(List.of("Mcp-Session-Id", "WWW-Authenticate", "MCP-Protocol-Version"));
        cors.setMaxAge(600L);
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        http.securityMatcher("/mcp").cors(value -> value.configurationSource(source))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .securityContext(context -> context.securityContextRepository(new NullSecurityContextRepository()))
                .requestCache(cache -> cache.requestCache(new NullRequestCache())).csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable).httpBasic(AbstractHttpConfigurer::disable).logout(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authorize -> authorize.dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll().anyRequest().authenticated())
                .exceptionHandling(errors -> errors.authenticationEntryPoint((request, response, exception) -> {
                    response.setStatus(401);
                    response.setContentType("application/json;charset=UTF-8");
                    mapper.writeValue(response.getOutputStream(), Map.of("error", "invalid_token"));
                }));
        http.addFilterBefore(new McpResourceFilter(authentication, endpoints, mapper, admission), AnonymousAuthenticationFilter.class);
        http.addFilterBefore(new McpAuditFilter(audit), CorsFilter.class);
        return http.build();
    }
}
