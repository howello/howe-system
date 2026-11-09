package com.howe.mcp.service.security;

import cn.hutool.core.io.IoUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.howe.common.config.ConfigProvider;
import com.howe.common.constant.ConfigConstants;
import com.howe.mcp.service.McpProtocolAdmission;
import com.howe.mcp.service.McpCimdClientService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/** 在框架协议过滤器前限制实际开放路径，并有界解析表单，防止参数污染与未声明能力外放。 */
@Slf4j
@RequiredArgsConstructor
public final class McpOAuthProtocolFilter extends OncePerRequestFilter {
    private static final Set<String> CONSENT_FIELDS = Set.of("client_id", "state", "scope", "_csrf");
    private final ObjectMapper mapper;
    private final ConfigProvider config;
    private final McpProtocolAdmission admission;
    private final McpCimdClientService cimd;

    /** 只允许初始授权、用户确认、令牌及两类元数据；其它 OAuth 路径返回协议 JSON。 */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        String method = request.getMethod();
        boolean authorize = "/oauth2/authorize".equals(path);
        boolean token = "/oauth2/token".equals(path);
        boolean revoke = "/oauth2/revoke".equals(path);
        boolean metadata = "/.well-known/oauth-authorization-server".equals(path)
                || "/.well-known/oauth-protected-resource/mcp".equals(path);
        if (!authorize && !token && !revoke && !metadata) {
            error(response, 404, "unsupported_endpoint");
            return;
        }
        if (!(authorize && ("GET".equals(method) || "POST".equals(method)))
                && !((token || revoke) && "POST".equals(method)) && !(metadata && "GET".equals(method))) {
            error(response, 405, "invalid_request");
            return;
        }
        try {
            var bucket = authorize ? McpProtocolAdmission.Bucket.AUTHORIZE_IP : token ? McpProtocolAdmission.Bucket.TOKEN_IP
                    : revoke ? McpProtocolAdmission.Bucket.REVOKE_IP : McpProtocolAdmission.Bucket.METADATA_IP;
            admission.acquire(bucket, request.getRemoteAddr());
        } catch (McpProtocolAdmission.Rejected exception) {
            if (exception.status() == 429) { response.setHeader("Retry-After", Integer.toString(exception.retryAfter())); }
            error(response, exception.status(), exception.status() == 429 ? "rate_limit_exceeded" : "temporarily_unavailable");
            return;
        }
        boolean enabled;
        try {
            enabled = metadata || revoke || "true".equalsIgnoreCase(config.getPlainValue(ConfigConstants.MCP_ENABLED));
        } catch (RuntimeException exception) {
            error(response, 503, "temporarily_unavailable");
            return;
        }
        if (!enabled) {
            error(response, 503, "temporarily_unavailable");
            return;
        }
        if (!"POST".equals(method)) {
            if (authorize && !cimd(request, response)) { return; }
            forward(request, response, chain);
            return;
        }
        HttpServletRequest wrapped;
        try {
            if (request.getQueryString() != null || request.getContentLengthLong() > 16384 || !form(request.getContentType())) {
                throw new IllegalArgumentException();
            }
            byte[] body = IoUtil.readBytes(request.getInputStream(), 16385);
            if (body.length > 16384) {
                throw new IllegalArgumentException();
            }
            Map<String, String[]> parameters = parameters(body, authorize);
            if (authorize && !CONSENT_FIELDS.containsAll(parameters.keySet())) {
                throw new IllegalArgumentException();
            }
            if (token && "authorization_code".equals(first(parameters, "grant_type"))) {
                String verifier = first(parameters, "code_verifier");
                if (verifier == null || !verifier.matches("[A-Za-z0-9._~-]{43,128}")) {
                    throw new IllegalArgumentException();
                }
            }
            wrapped = new HttpServletRequestWrapper(request) {
                /** 框架仅从已按 UTF-8 有界解析的表单读取单值参数。 */
                @Override
                public String getParameter(String name) {
                    return first(parameters, name);
                }

                /** 用户同意范围允许多选，返回独立数组避免下游修改共享值。 */
                @Override
                public String[] getParameterValues(String name) {
                    String[] values = parameters.get(name);
                    return values == null ? null : values.clone();
                }

                /** 不把原始 Servlet 参数表中可能混入的查询值重新交给框架。 */
                @Override
                public Map<String, String[]> getParameterMap() {
                    Map<String, String[]> copy = new LinkedHashMap<>();
                    parameters.forEach((name, values) -> copy.put(name, values.clone()));
                    return Collections.unmodifiableMap(copy);
                }

                /** 参数名称与本次已校验的表单保持一致。 */
                @Override
                public Enumeration<String> getParameterNames() {
                    return Collections.enumeration(parameters.keySet());
                }
            };
        } catch (IllegalArgumentException exception) {
            error(response, 400, "invalid_request");
            return;
        }
        if ((authorize || token) && !cimd(wrapped, response)) { return; }
        forward(wrapped, response, chain);
    }

    /** 只在已完成来源配额与有界解析后发现 HTTPS 客户端；撤销不依赖远端文档可用性。 */
    private boolean cimd(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            if (request.getQueryString() != null && request.getQueryString().length() > 16384) {
                error(response, 400, "invalid_request"); return false;
            }
            String[] identifiers = request.getParameterValues("client_id");
            if (identifiers != null && identifiers.length != 1) { error(response, 400, "invalid_request"); return false; }
            cimd.ensure(identifiers == null ? null : identifiers[0], request.getRemoteAddr());
            return true;
        } catch (McpMetadataException exception) {
            error(response, exception.isTransientFailure() ? 503 : 400, exception.isTransientFailure() ? "temporarily_unavailable" : "invalid_client");
        } catch (McpRegistrationException exception) {
            if (exception.getStatus() == 429) { response.setHeader("Retry-After", "60"); }
            error(response, exception.getStatus(), "temporarily_unavailable");
        } catch (RuntimeException exception) {
            log.warn("MCP 客户端元数据入口暂不可用");
            error(response, 503, "temporarily_unavailable");
        }
        return false;
    }

    /** 框架之外的基础设施异常采用稳定 JSON，不记录异常报文中的潜在凭据或 SQL 参数。 */
    private void forward(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        try {
            chain.doFilter(request, response);
        } catch (RuntimeException exception) {
            if (response.isCommitted()) {
                throw exception;
            }
            log.warn("MCP OAuth 协议入口暂不可用");
            response.resetBuffer();
            error(response, 503, "temporarily_unavailable");
        }
    }

    /** 表单参数按标准加号及百分号规则解码，只允许同意提交的 scope 重复。 */
    private static Map<String, String[]> parameters(byte[] body, boolean consent) {
        Map<String, List<String>> values = new LinkedHashMap<>();
        for (String part : new String(body, StandardCharsets.UTF_8).split("&")) {
            if (part.isEmpty()) {
                continue;
            }
            int separator = part.indexOf('=');
            String name = URLDecoder.decode(separator < 0 ? part : part.substring(0, separator), StandardCharsets.UTF_8);
            String value = URLDecoder.decode(separator < 0 ? "" : part.substring(separator + 1), StandardCharsets.UTF_8);
            if (name.isEmpty() || values.size() >= 32 && !values.containsKey(name)) {
                throw new IllegalArgumentException();
            }
            List<String> entries = values.computeIfAbsent(name, key -> new ArrayList<>());
            if (!entries.isEmpty() && !(consent && "scope".equals(name))) {
                throw new IllegalArgumentException();
            }
            entries.add(value);
        }
        Map<String, String[]> result = new LinkedHashMap<>();
        values.forEach((name, entries) -> result.put(name, entries.toArray(String[]::new)));
        return result;
    }

    /** 只接受明确的标准表单类型，不接受通配符和其它结构化报文。 */
    private static boolean form(String value) {
        if (value == null) {
            return false;
        }
        MediaType actual = MediaType.parseMediaType(value);
        return "application".equals(actual.getType()) && "x-www-form-urlencoded".equals(actual.getSubtype())
                && (actual.getCharset() == null || StandardCharsets.UTF_8.equals(actual.getCharset()));
    }

    /** 返回已经拒绝重复值的表单参数首值。 */
    private static String first(Map<String, String[]> parameters, String name) {
        String[] values = parameters.get(name);
        return values == null || values.length == 0 ? null : values[0];
    }

    /** 标准协议错误不进入后台 AjaxResult 或容器 HTML 错误页。 */
    public void error(HttpServletResponse response, int status, String code) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        mapper.writeValue(response.getOutputStream(), Map.of("error", code));
    }
}
