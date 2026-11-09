package com.howe.mcp.service.security;

import cn.hutool.core.io.IoUtil;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.howe.mcp.config.McpEndpointSettings;
import com.howe.mcp.dto.McpJsonRpcError;
import com.howe.mcp.service.McpProtocolAdmission;
import com.howe.mcp.service.McpBearerAuthenticationService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.web.filter.OncePerRequestFilter;

/** MCP 资源的独立 Bearer 与 HTTP 输入边界，不接受 Cookie、查询 token 或原后台 JWT。 */
@Slf4j
public final class McpResourceFilter extends OncePerRequestFilter {
    public static final String MESSAGE_ATTRIBUTE = "howe.mcp.message";
    private static final Pattern BEARER = Pattern.compile("^Bearer ([A-Za-z0-9._~+/-]+=*)$", Pattern.CASE_INSENSITIVE);
    private final McpBearerAuthenticationService authentication;
    private final McpEndpointSettings endpoints;
    private final ObjectMapper mapper;
    private final ObjectReader reader;
    private final McpProtocolAdmission admission;

    /** JSON 解析使用独立约束，不修改项目全局 ObjectMapper。 */
    public McpResourceFilter(McpBearerAuthenticationService authentication, McpEndpointSettings endpoints, ObjectMapper mapper,
            McpProtocolAdmission admission) {
        this.authentication = authentication;
        this.endpoints = endpoints;
        this.mapper = mapper;
        this.admission = admission;
        var bounded = mapper.copy();
        bounded.getFactory().setStreamReadConstraints(StreamReadConstraints.builder()
                .maxNestingDepth(32).maxStringLength(32768).maxNumberLength(128).build());
        reader = bounded.readerFor(JsonNode.class).with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .with(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    }

    /** 每个 HTTP 请求都重新认证，再把不可变身份传给真正的协议处理线程。 */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        response.setHeader("Cache-Control", "no-store");
        List<String> origins = Collections.list(request.getHeaders("Origin"));
        if (origins.size() > 1 || origins.size() == 1 && !endpoints.allowedOrigins().contains(origins.get(0))) {
            error(response, 403, "invalid_origin");
            return;
        }
        if (!List.of("GET", "POST", "DELETE").contains(request.getMethod())) {
            response.setHeader("Allow", "GET, POST, DELETE, OPTIONS");
            error(response, 405, "invalid_request");
            return;
        }
        if (!acquire(response, McpProtocolAdmission.Bucket.MCP_IP, request.getRemoteAddr())) { return; }
        // 不调用 Servlet 的表单参数解析，防止在有界读取之前被迫解析不受信任的大表单。
        String query = request.getQueryString();
        boolean queryToken = false;
        try {
            if (query != null && query.length() > 2048) { throw new IllegalArgumentException(); }
            if (query != null) {
                for (String part : query.split("&")) {
                    String name = part.split("=", 2)[0];
                    queryToken |= "access_token".equals(java.net.URLDecoder.decode(name, StandardCharsets.UTF_8));
                }
            }
        } catch (IllegalArgumentException exception) {
            challenge(response, 400, "invalid_request");
            return;
        }
        if (queryToken) {
            challenge(response, 400, "invalid_request");
            return;
        }
        List<String> headers = Collections.list(request.getHeaders("Authorization"));
        if (headers.isEmpty()) {
            challenge(response, 401, null);
            return;
        }
        if (headers.size() != 1 || headers.get(0).length() > 8200) {
            challenge(response, 400, "invalid_request");
            return;
        }
        var bearer = BEARER.matcher(headers.get(0));
        if (!bearer.matches()) {
            challenge(response, 401, "invalid_token");
            return;
        }
        McpBearerIdentity identity;
        try {
            identity = authentication.authenticate(bearer.group(1), request.getRemoteAddr());
        } catch (McpMetadataException exception) {
            if (exception.isTransientFailure()) { error(response, 503, "temporarily_unavailable"); }
            else { challenge(response, 401, "invalid_token"); }
            return;
        } catch (McpRegistrationException exception) {
            if (exception.getStatus() == 429) { response.setHeader("Retry-After", "60"); }
            error(response, exception.getStatus(), "temporarily_unavailable");
            return;
        } catch (OAuth2AuthenticationException exception) {
            if ("temporarily_unavailable".equals(exception.getError().getErrorCode())) {
                error(response, 503, "temporarily_unavailable");
            } else {
                challenge(response, 401, "invalid_token");
            }
            return;
        } catch (RuntimeException exception) {
            log.warn("MCP Bearer 认证暂不可用");
            error(response, 503, "temporarily_unavailable");
            return;
        }
        if (!acquire(response, McpProtocolAdmission.Bucket.MCP_USER, identity.invocation().userId().toString())) { return; }
        for (String name : List.of("Mcp-Session-Id", "MCP-Protocol-Version", "Last-Event-ID")) {
            List<String> values = Collections.list(request.getHeaders(name));
            if (values.size() > 1 || values.size() == 1 && (values.get(0).isBlank() || values.get(0).length() > 128)) {
                error(response, 400, "invalid_request");
                return;
            }
        }
        HttpServletRequest incoming = request;
        if ("POST".equals(request.getMethod())) {
            try {
                MediaType type = request.getContentType() == null ? null : MediaType.parseMediaType(request.getContentType());
                if (type == null || !"application".equals(type.getType()) || !"json".equals(type.getSubtype())
                        || type.getCharset() != null && !StandardCharsets.UTF_8.equals(type.getCharset())) {
                    error(response, 415, "invalid_request");
                    return;
                }
                if (request.getContentLengthLong() > 65536) {
                    error(response, 413, "request_too_large");
                    return;
                }
                byte[] body = IoUtil.readBytes(request.getInputStream(), 65537);
                if (body.length > 65536) {
                    error(response, 413, "request_too_large");
                    return;
                }
                JsonNode message = reader.readValue(body);
                if (!envelope(message)) {
                    rpcError(response, 400, null, -32600, "JSON-RPC 请求无效");
                    return;
                }
                request.setAttribute(MESSAGE_ATTRIBUTE, message);
                incoming = replayable(request, body);
            } catch (com.fasterxml.jackson.core.JacksonException exception) {
                rpcError(response, 400, null, -32700, "JSON 格式无效");
                return;
            } catch (IllegalArgumentException exception) {
                error(response, 400, "invalid_request");
                return;
            }
        }
        incoming.setAttribute(McpBearerIdentity.REQUEST_ATTRIBUTE, identity);
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(identity, null, List.of()));
        SecurityContextHolder.setContext(context);
        try {
            chain.doFilter(incoming, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    /** 仅接受单个标准 JSON-RPC 对象，批量报文和非法 ID 不能穿过输入边界。 */
    private static boolean envelope(JsonNode value) {
        if (value == null || !value.isObject() || !"2.0".equals(value.path("jsonrpc").asText())) {
            return false;
        }
        JsonNode id = value.get("id");
        if (id != null && !(id.isTextual() && id.textValue().length() <= 128 || id.isIntegralNumber() && id.canConvertToLong())) {
            return false;
        }
        if (value.has("method")) {
            return value.get("method").isTextual() && !value.get("method").textValue().isBlank()
                    && value.get("method").textValue().length() <= 128
                    && (!value.has("params") || value.get("params").isObject());
        }
        return id != null && value.has("result") != value.has("error");
    }

    /** 只有已校验且有界的字节才提供给 SDK 再解析，原始流不会被重复读取或无界缓冲。 */
    private static HttpServletRequest replayable(HttpServletRequest request, byte[] body) {
        return new HttpServletRequestWrapper(request) {
            /** 为框架提供独立的内存输入流。 */
            @Override
            public ServletInputStream getInputStream() {
                var source = IoUtil.toStream(body);
                return new ServletInputStream() {
                    /** 读取一个有界内存字节。 */
                    @Override
                    public int read() { return source.read(); }
                    /** 读取已有内存块，不访问原始 HTTP 流。 */
                    @Override
                    public int read(byte[] bytes, int offset, int length) { return source.read(bytes, offset, length); }
                    /** 字节已经全部可用。 */
                    @Override
                    public boolean isReady() { return true; }
                    /** 判断有界内存流是否读完。 */
                    @Override
                    public boolean isFinished() { return source.available() == 0; }
                    /** 内存内容一次就绪，不创建异步读线程。 */
                    @Override
                    public void setReadListener(ReadListener listener) {
                        try {
                            if (!isFinished()) { listener.onDataAvailable(); }
                            if (isFinished()) { listener.onAllDataRead(); }
                        } catch (IOException exception) { listener.onError(exception); }
                    }
                };
            }
            /** JSON 按 UTF-8 读取，不继承 Servlet 的缺省字符集。 */
            @Override
            public BufferedReader getReader() { return IoUtil.getReader(getInputStream(), StandardCharsets.UTF_8); }
            /** 框架读取的是缓存字节长度。 */
            @Override
            public int getContentLength() { return body.length; }
            /** 分块输入重放时同样报告已校验的实际长度。 */
            @Override
            public long getContentLengthLong() { return body.length; }
        };
    }

    /** RFC 9728 发现始终使用可信地址，错误不回显提交的凭据。 */
    private void challenge(HttpServletResponse response, int status, String code) throws IOException {
        response.setHeader("WWW-Authenticate", "Bearer resource_metadata=\"" + endpoints.resourceMetadata()
                + "\", scope=\"" + McpScopes.CONNECT + "\"" + (code == null ? "" : ", error=\"" + code + "\""));
        error(response, status, code == null ? "authentication_required" : code);
    }

    /** HTTP/OAuth 边界错误采用固定 JSON，协议解析错误另用标准 JSON-RPC。 */
    private void error(HttpServletResponse response, int status, String code) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        mapper.writeValue(response.getOutputStream(), Map.of("error", code));
    }

    /** IP 仅采用容器提供的远端地址，代理地址的规范化应由部署可信代理配置完成。 */
    private boolean acquire(HttpServletResponse response, McpProtocolAdmission.Bucket bucket, String subject) throws IOException {
        try {
            admission.acquire(bucket, subject);
            return true;
        } catch (McpProtocolAdmission.Rejected exception) {
            if (exception.status() == 429) { response.setHeader("Retry-After", Integer.toString(exception.retryAfter())); }
            error(response, exception.status(), exception.status() == 429 ? "rate_limit_exceeded" : "temporarily_unavailable");
            return false;
        }
    }

    /** 不包含原报文或解析器错误片段的标准 JSON-RPC 错误。 */
    private void rpcError(HttpServletResponse response, int status, Object id, int code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        mapper.writeValue(response.getOutputStream(), new McpJsonRpcError(id, code, message));
    }
}
