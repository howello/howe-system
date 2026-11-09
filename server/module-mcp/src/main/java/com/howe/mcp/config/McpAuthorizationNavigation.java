package com.howe.mcp.config;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

/** 只保存经过协议校验的初始 GET 授权请求，不接管原后台请求缓存。 */
public record McpAuthorizationNavigation(HttpSessionRequestCache requests) {

    /** 重定向到前端登录后，服务端专属恢复入口从这个 Session 缓存重建可信后端 URL。 */
    public static McpAuthorizationNavigation create() {
        var requests = new HttpSessionRequestCache() {
            /** 只缓存恢复必需的协议字段，不能把 Authorization、Cookie 或无关查询字段放入 Redis Session。 */
            @Override
            public void saveRequest(HttpServletRequest request, HttpServletResponse response) {
                Set<String> allowed = Set.of("response_type", "client_id", "redirect_uri", "scope", "state", "resource",
                        "code_challenge", "code_challenge_method", "response_mode");
                Map<String, String[]> parameters = new LinkedHashMap<>();
                var query = UriComponentsBuilder.newInstance();
                request.getParameterMap().forEach((name, values) -> {
                    if (allowed.contains(name)) {
                        parameters.put(name, values.clone());
                        query.queryParam(name, java.util.Arrays.stream(values)
                                .map(value -> UriUtils.encode(value, StandardCharsets.UTF_8)).toArray(Object[]::new));
                    }
                });
                HttpServletRequest safe = new HttpServletRequestWrapper(request) {
                    /** 原始认证头及客户端标识头不进入请求缓存。 */
                    @Override
                    public Enumeration<String> getHeaderNames() { return Collections.emptyEnumeration(); }
                    /** 不提供任何原始请求头的间接读取路径。 */
                    @Override
                    public Enumeration<String> getHeaders(String name) { return Collections.emptyEnumeration(); }
                    /** 请求缓存无需凭据或头部元数据。 */
                    @Override
                    public String getHeader(String name) { return null; }
                    /** OAuth Session 只保留自己的服务端状态，不再嵌套保存浏览器 Cookie。 */
                    @Override
                    public Cookie[] getCookies() { return null; }
                    /** 仅保存已经由授权入口校验的核心参数。 */
                    @Override
                    public Map<String, String[]> getParameterMap() { return Collections.unmodifiableMap(parameters); }
                    /** 查询串也必须重建，避免 DefaultSavedRequest 额外保存原始无关字段。 */
                    @Override
                    public String getQueryString() { return query.build(true).getQuery(); }
                };
                super.saveRequest(safe, response);
            }
        };
        requests.setRequestMatcher(request -> "GET".equals(request.getMethod())
                && "/oauth2/authorize".equals(request.getRequestURI().substring(request.getContextPath().length())));
        requests.setMatchingRequestParameterName(null);
        return new McpAuthorizationNavigation(requests);
    }
}
