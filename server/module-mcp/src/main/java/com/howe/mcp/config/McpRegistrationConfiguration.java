package com.howe.mcp.config;

import com.howe.mcp.service.impl.McpClientRegistrationServiceImpl;
import com.howe.mcp.service.impl.McpRegistrationAdmissionImpl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.howe.common.config.ConfigProvider;
import com.howe.mcp.service.McpClientRegistrationService;
import com.howe.mcp.service.McpRegistrationAdmission;
import com.howe.mcp.service.McpAuditService;
import com.howe.mcp.service.security.McpAuditFilter;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.NullSecurityContextRepository;
import org.springframework.security.web.savedrequest.NullRequestCache;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/** 动态登记使用无状态协议链，浏览器任意兼容客户端可登记但不会得到用户授权。 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "howe.mcp", name = "installed", havingValue = "true")
public class McpRegistrationConfiguration {

    /** 客户端 secret 使用独立委派编码器，不修改原用户密码编码器。 */
    @Bean
    public McpClientRegistrationService mcpClientRegistration(McpModuleConfiguration.Persistence persistence,
            JdbcRegisteredClientRepository clients, ConfigProvider config, ObjectMapper mapper) {
        return new McpClientRegistrationServiceImpl(persistence.jdbc(), persistence.transactions(), clients, config,
                PasswordEncoderFactories.createDelegatingPasswordEncoder(), mapper, Clock.systemUTC());
    }

    /** 每 IP 配额共享 Redis，不向全局发布另一个 RedisTemplate Bean。 */
    @Bean
    public McpRegistrationAdmission mcpRegistrationAdmission(RedisConnectionFactory connections, ConfigProvider config) {
        return new McpRegistrationAdmissionImpl(new StringRedisTemplate(connections), config, Clock.systemUTC());
    }

    /** 公开协议请求不使用用户会话，无 CSRF Cookie 认证；仅允许 POST 登记和必要预检。 */
    @Bean
    @Order(4)
    public SecurityFilterChain mcpRegistrationSecurity(HttpSecurity http, ObjectMapper mapper, McpAuditService audit) throws Exception {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(List.of("*"));
        cors.setAllowedMethods(List.of("POST", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Content-Type"));
        cors.setAllowCredentials(false);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        http.addFilterBefore(new McpAuditFilter(audit), CorsFilter.class);
        return http.securityMatcher("/oauth2/register").cors(value -> value.configurationSource(source))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .securityContext(context -> context.securityContextRepository(new NullSecurityContextRepository()))
                .requestCache(cache -> cache.requestCache(new NullRequestCache())).csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable).httpBasic(AbstractHttpConfigurer::disable).logout(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authorize -> authorize.requestMatchers(HttpMethod.POST, "/oauth2/register").permitAll().anyRequest().denyAll())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) -> methodNotAllowed(response, mapper))
                        .accessDeniedHandler((request, response, exception) -> methodNotAllowed(response, mapper))).build();
    }

    /** 协议入口不返回默认 HTML 登录页或后台 AjaxResult。 */
    private static void methodNotAllowed(jakarta.servlet.http.HttpServletResponse response, ObjectMapper mapper) throws java.io.IOException {
        response.setStatus(405);
        response.setContentType("application/json;charset=UTF-8");
        mapper.writeValue(response.getOutputStream(), Map.of("error", "invalid_request"));
    }
}
