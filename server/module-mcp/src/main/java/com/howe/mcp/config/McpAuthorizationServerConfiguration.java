package com.howe.mcp.config;

import com.howe.mcp.service.impl.McpTokenRevocationServiceImpl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.howe.common.config.ConfigProvider;
import com.howe.mcp.service.security.McpAccessTokenCustomizer;
import com.howe.mcp.service.security.McpAuthorizationRequestConverter;
import com.howe.mcp.service.security.McpAuthorizationRequestValidator;
import com.howe.mcp.service.security.McpAuthorizationResponseHandler;
import com.howe.mcp.service.security.McpConsentAuthenticationProvider;
import com.howe.mcp.service.security.McpOAuthProtocolFilter;
import com.howe.mcp.service.security.McpPublicClientAuthenticationProvider;
import com.howe.mcp.service.security.McpPublicRefreshAuthenticationConverter;
import com.howe.mcp.service.security.McpPublicRevocationClientConverter;
import com.howe.mcp.service.security.McpRefreshTokenGenerator;
import com.howe.mcp.service.security.McpResourceBoundAuthenticationProvider;
import com.howe.mcp.service.security.McpScopes;
import com.howe.mcp.service.security.McpTokenAccountAuthenticationProvider;
import com.howe.mcp.service.security.McpTransactionalAuthenticationProvider;
import com.howe.mcp.service.security.McpTokenRevocationAuthenticationProvider;
import com.howe.mcp.service.McpJdbcAuthorizationService;
import com.howe.mcp.service.McpProtocolAdmission;
import com.howe.mcp.service.McpAuditService;
import com.howe.mcp.service.security.McpAuditFilter;
import com.howe.mcp.service.security.McpCimdAuthorizationProvider;
import com.howe.mcp.service.McpCimdClientService;
import com.howe.mcp.service.McpTokenRevocationService;
import com.howe.mcp.service.spi.McpAccountBridge;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.authentication.ClientSecretAuthenticationProvider;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeAuthenticationProvider;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationProvider;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2RefreshTokenAuthenticationProvider;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.token.DelegatingOAuth2TokenGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2AccessTokenGenerator;
import org.springframework.security.oauth2.server.authorization.web.authentication.ClientSecretBasicAuthenticationConverter;
import org.springframework.security.oauth2.server.authorization.web.authentication.ClientSecretPostAuthenticationConverter;
import org.springframework.security.oauth2.server.authorization.web.authentication.OAuth2AuthorizationCodeAuthenticationConverter;
import org.springframework.security.oauth2.server.authorization.web.authentication.OAuth2AuthorizationConsentAuthenticationConverter;
import org.springframework.security.oauth2.server.authorization.web.authentication.OAuth2RefreshTokenAuthenticationConverter;
import org.springframework.security.oauth2.server.authorization.web.authentication.PublicClientAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.NullSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.savedrequest.NullRequestCache;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/** 显式装配浏览器授权与无状态协议两条链，不发布 OAuth provider 为全局密码认证组件。 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "howe.mcp", name = "installed", havingValue = "true")
public class McpAuthorizationServerConfiguration {
    private final McpEndpointSettings endpoints;
    private final McpModuleConfiguration.Persistence persistence;
    private final JdbcRegisteredClientRepository clients;
    private final JdbcOAuth2AuthorizationConsentService consents;
    private final McpJdbcAuthorizationService authorizations;
    private final McpAccountBridge accounts;
    private final ConfigProvider config;
    private final ObjectMapper mapper;
    private final McpBrowserState browser;
    private final McpProtocolAdmission admission;
    private final McpAuditService audit;
    private final McpCimdClientService cimd;

    /** 请求缓存封装为模块对象，不发布全局 RequestCache Bean。 */
    @Bean
    public McpAuthorizationNavigation mcpAuthorizationNavigation() {
        return McpAuthorizationNavigation.create();
    }

    /** 浏览器只在授权端点读取 Session，POST 同意另加实际执行的 CSRF 过滤器。 */
    @Bean
    @Order(6)
    public SecurityFilterChain mcpAuthorizationSecurity(HttpSecurity http, McpAuthorizationNavigation navigation) throws Exception {
        var gate = new McpOAuthProtocolFilter(mapper, config, admission, cimd);
        var csrf = new CsrfFilter(browser.csrf());
        csrf.setBeanName("mcpAuthorizationCsrf");
        csrf.setRequestHandler(new CsrfTokenRequestAttributeHandler());
        csrf.setRequireCsrfProtectionMatcher(request -> "POST".equals(request.getMethod()));
        csrf.setAccessDeniedHandler((request, response, exception) -> gate.error(response, 403, "request_rejected"));
        var responses = new McpAuthorizationResponseHandler(endpoints, mapper, clients);
        var server = server(http);
        server.authorizationEndpoint(endpoint -> endpoint.consentPage(endpoints.consentPage())
                .authorizationRequestConverters(converters -> {
                    converters.clear();
                    converters.add(new McpAuthorizationRequestConverter());
                    converters.add(new OAuth2AuthorizationConsentAuthenticationConverter());
                }).authorizationResponseHandler(responses).errorResponseHandler(responses));
        http.securityMatcher("/oauth2/authorize")
                .cors(cors -> cors.configurationSource(browserCors()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .securityContext(context -> context.securityContextRepository(browser.contexts()).requireExplicitSave(true))
                .requestCache(cache -> cache.requestCache(navigation.requests()))
                .csrf(value -> value.csrfTokenRepository(browser.csrf()).csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
                .exceptionHandling(errors -> errors.authenticationEntryPoint((request, response, exception) -> {
                    response.setHeader("Cache-Control", "no-store");
                    response.sendRedirect(endpoints.loginPage() + "?flow=authorize");
                }).accessDeniedHandler((request, response, exception) -> gate.error(response, 403, "request_rejected")))
                .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                .formLogin(AbstractHttpConfigurer::disable).httpBasic(AbstractHttpConfigurer::disable).logout(AbstractHttpConfigurer::disable);
        http.addFilterAfter(gate, CorsFilter.class).addFilterAfter(csrf, McpOAuthProtocolFilter.class);
        http.addFilterBefore(new McpAuditFilter(audit), CorsFilter.class);
        return http.build();
    }

    /** token 不读取 OAuth 浏览器 Cookie，未开放的 OAuth 路径也不会落入后台 JWT 链。 */
    @Bean
    @Order(7)
    public SecurityFilterChain mcpProtocolSecurity(HttpSecurity http) throws Exception {
        var gate = new McpOAuthProtocolFilter(mapper, config, admission, cimd);
        var server = server(http);
        server.tokenEndpoint(endpoint -> endpoint.accessTokenRequestConverters(converters -> {
            converters.clear();
            converters.add(new OAuth2AuthorizationCodeAuthenticationConverter());
            converters.add(new OAuth2RefreshTokenAuthenticationConverter());
        }));
        server.authorizationServerMetadataEndpoint(metadata -> metadata.authorizationServerMetadataCustomizer(builder -> builder.claims(claims -> {
            claims.clear();
            claims.put("issuer", endpoints.issuer());
            claims.put("authorization_endpoint", endpoints.issuer() + "/oauth2/authorize");
            claims.put("token_endpoint", endpoints.issuer() + "/oauth2/token");
            claims.put("registration_endpoint", endpoints.issuer() + "/oauth2/register");
            claims.put("client_id_metadata_document_supported", true);
            claims.put("revocation_endpoint", endpoints.issuer() + "/oauth2/revoke");
            claims.put("revocation_endpoint_auth_methods_supported", List.of("none", "client_secret_basic", "client_secret_post"));
            claims.put("response_types_supported", List.of("code"));
            claims.put("response_modes_supported", List.of("query"));
            claims.put("grant_types_supported", List.of("authorization_code", "refresh_token"));
            claims.put("token_endpoint_auth_methods_supported", List.of("none", "client_secret_basic", "client_secret_post"));
            claims.put("code_challenge_methods_supported", List.of("S256"));
            claims.put("scopes_supported", List.copyOf(McpScopes.SUPPORTED));
            claims.put("authorization_response_iss_parameter_supported", true);
        })));
        http.securityMatcher("/oauth2/**", "/.well-known/oauth-authorization-server", "/.well-known/oauth-protected-resource/mcp")
                .cors(cors -> cors.configurationSource(publicCors()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .securityContext(context -> context.securityContextRepository(new NullSecurityContextRepository()))
                .requestCache(cache -> cache.requestCache(new NullRequestCache())).csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                .formLogin(AbstractHttpConfigurer::disable).httpBasic(AbstractHttpConfigurer::disable).logout(AbstractHttpConfigurer::disable);
        http.addFilterAfter(gate, CorsFilter.class);
        http.addFilterBefore(new McpAuditFilter(audit), CorsFilter.class);
        return http.build();
    }

    /** 所有链使用可信 issuer 和 opaque 生成器；默认 JWT/JWK 与隐式 Agent 能力不参与。 */
    private OAuth2AuthorizationServerConfigurer server(HttpSecurity http) throws Exception {
        var server = OAuth2AuthorizationServerConfigurer.authorizationServer();
        var requests = new OAuth2AuthorizationCodeRequestAuthenticationProvider(clients, authorizations, consents);
        requests.setAuthenticationValidator(new McpAuthorizationRequestValidator(endpoints));
        requests.setAuthorizationConsentRequired(context -> true);
        var confirmations = new McpConsentAuthenticationProvider(persistence.jdbc(), persistence.transactions(),
                clients, authorizations, consents, accounts, config, endpoints, cimd);
        var initial = new McpCimdAuthorizationProvider(requests, cimd);
        var secrets = new ClientSecretAuthenticationProvider(clients, authorizations);
        secrets.setPasswordEncoder(PasswordEncoderFactories.createDelegatingPasswordEncoder());
        var publicClients = new McpPublicClientAuthenticationProvider(clients, authorizations);
        var codes = exchange(new OAuth2AuthorizationCodeAuthenticationProvider(authorizations, generators()));
        var refreshes = exchange(new OAuth2RefreshTokenAuthenticationProvider(authorizations, generators()));
        var revocations = new McpTokenRevocationAuthenticationProvider(
                new McpTokenRevocationServiceImpl(persistence.jdbc(), persistence.transactions()));
        // 显式无父级管理器：ProviderManager 的认证异常不能继续回退到原后台全局管理器。
        http.authenticationManager(new ProviderManager(List.of(secrets, publicClients, initial, confirmations, codes, refreshes, revocations)));
        http.with(server, value -> value.registeredClientRepository(clients).authorizationService(authorizations).authorizationConsentService(consents)
                .authorizationServerSettings(AuthorizationServerSettings.builder().issuer(endpoints.issuer()).build())
                .tokenGenerator(generators()));
        server.authorizationEndpoint(endpoint -> endpoint.authenticationProviders(providers -> {
            providers.clear();
            providers.add(initial);
            providers.add(confirmations);
        }));
        server.tokenEndpoint(endpoint -> endpoint.authenticationProviders(providers -> {
            providers.clear();
            providers.add(codes);
            providers.add(refreshes);
        }));
        server.tokenRevocationEndpoint(endpoint -> endpoint.authenticationProviders(providers -> {
            providers.clear();
            providers.add(revocations);
        }));
        server.clientAuthentication(client -> client.authenticationConverters(converters -> {
            converters.clear();
            converters.add(new McpPublicRevocationClientConverter());
            converters.add(new McpPublicRefreshAuthenticationConverter());
            converters.add(new ClientSecretBasicAuthenticationConverter());
            converters.add(new ClientSecretPostAuthenticationConverter());
            converters.add(new PublicClientAuthenticationConverter());
        }).authenticationProviders(providers -> {
            providers.clear();
            providers.add(secrets);
            providers.add(publicClients);
        }));
        return server;
    }

    /** 资源和当前账号复核均位于整笔兑换事务内；不会留下原生 provider 作为认证失败回退。 */
    private AuthenticationProvider exchange(AuthenticationProvider provider) {
        return new McpTransactionalAuthenticationProvider(new McpResourceBoundAuthenticationProvider(
                new McpTokenAccountAuthenticationProvider(provider, authorizations, accounts, config), endpoints),
                persistence.transactions());
    }

    /** 发行固定受众的不透明 access token 与有绝对截止时间的 public refresh。 */
    private DelegatingOAuth2TokenGenerator generators() {
        var access = new OAuth2AccessTokenGenerator();
        access.setAccessTokenCustomizer(new McpAccessTokenCustomizer(endpoints));
        return new DelegatingOAuth2TokenGenerator(access, new McpRefreshTokenGenerator());
    }

    /** 浏览器授权允许精确来源和 Cookie，跨域用户同意需要 CSRF 头。 */
    private UrlBasedCorsConfigurationSource browserCors() {
        var cors = new CorsConfiguration();
        cors.setAllowedOrigins(List.copyOf(endpoints.allowedOrigins()));
        cors.setAllowCredentials(true);
        cors.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Content-Type", "X-MCP-CSRF-TOKEN"));
        return source(cors);
    }

    /** 公共元数据和客户端协议允许无 Cookie 的第三方来源，不以浏览器 Session 认证。 */
    private static UrlBasedCorsConfigurationSource publicCors() {
        var cors = new CorsConfiguration();
        cors.setAllowedOrigins(List.of("*"));
        cors.setAllowCredentials(false);
        cors.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Content-Type", "Authorization"));
        return source(cors);
    }

    /** 显式绑定到本链，避免全局 CORS Bean 接管旧后台策略。 */
    private static UrlBasedCorsConfigurationSource source(CorsConfiguration cors) {
        cors.setMaxAge(600L);
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return source;
    }
}
