package com.howe.mcp.config;

import jakarta.servlet.DispatcherType;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.session.data.redis.RedisSessionRepository;
import org.springframework.session.web.http.CookieHttpSessionIdResolver;
import org.springframework.session.web.http.DefaultCookieSerializer;
import org.springframework.session.web.http.SessionRepositoryFilter;

/** 仅在安装后创建独立 Redis 会话，并将过滤器限于 OAuth 路径。 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "howe.mcp", name = "installed", havingValue = "true")
public class McpBrowserSessionConfiguration {

    /** 使用独立命名空间及框架标准序列化；会话只存主键认证和 CSRF 等必要协议状态。 */
    @Bean
    public RedisSessionRepository mcpBrowserSessions(RedisConnectionFactory connections) {
        RedisTemplate<String, Object> redis = new RedisTemplate<>();
        redis.setConnectionFactory(connections);
        redis.setKeySerializer(new StringRedisSerializer());
        redis.setHashKeySerializer(new StringRedisSerializer());
        redis.setValueSerializer(new JdkSerializationRedisSerializer());
        redis.setHashValueSerializer(new JdkSerializationRedisSerializer());
        redis.afterPropertiesSet();
        RedisSessionRepository sessions = new RedisSessionRepository(redis);
        sessions.setRedisKeyNamespace("howe:mcp:sessions");
        sessions.setDefaultMaxInactiveInterval(Duration.ofMinutes(30));
        return sessions;
    }

    /** OAuth 之外的业务接口不会经过 Spring Session 过滤器。 */
    @Bean
    public FilterRegistrationBean<SessionRepositoryFilter<?>> mcpBrowserSessionFilter(RedisSessionRepository sessions,
            McpEndpointSettings endpoints) {
        SessionRepositoryFilter<?> filter = new SessionRepositoryFilter<>(sessions);
        filter.setHttpSessionIdResolver(cookieResolver(endpoints));
        FilterRegistrationBean<SessionRepositoryFilter<?>> registration = new FilterRegistrationBean<>(filter);
        registration.setName("mcpBrowserSessionFilter");
        registration.addUrlPatterns("/oauth2/*");
        registration.setDispatcherTypes(DispatcherType.REQUEST, DispatcherType.ASYNC, DispatcherType.ERROR);
        registration.setOrder(SessionRepositoryFilter.DEFAULT_ORDER);
        return registration;
    }

    /** 生产 Cookie 使用 __Host 前缀、Secure 和 host-only；本机 HTTP 采用不同名称。 */
    public static CookieHttpSessionIdResolver cookieResolver(McpEndpointSettings endpoints) {
        boolean secure = "https".equals(endpoints.backendBaseUrl().getScheme());
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setCookieName(secure ? "__Host-HOWE_MCP_SESSION" : "HOWE_MCP_SESSION");
        serializer.setCookiePath("/");
        serializer.setUseHttpOnlyCookie(true);
        serializer.setUseSecureCookie(secure);
        serializer.setSameSite("Lax");
        CookieHttpSessionIdResolver resolver = new CookieHttpSessionIdResolver();
        resolver.setCookieSerializer(serializer);
        return resolver;
    }
}
