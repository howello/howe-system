package com.howe.mcp.service.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.howe.mcp.service.McpProtocolAdmission;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.filter.OncePerRequestFilter;

/** 浏览器登录与自助链的共享限流，先于 CSRF 和账号操作，不读取用户可伪造的来源头。 */
@Slf4j
@RequiredArgsConstructor
public final class McpBrowserAdmissionFilter extends OncePerRequestFilter {
    private final McpProtocolAdmission admission;
    private final ObjectMapper mapper;

    /** 登录提交单独计数，自助读取、撤销和退出使用另一窗口。 */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        var bucket = "/oauth2/login".equals(path) && "POST".equals(request.getMethod())
                ? McpProtocolAdmission.Bucket.LOGIN_IP : McpProtocolAdmission.Bucket.BROWSER_IP;
        try {
            admission.acquire(bucket, request.getRemoteAddr());
        } catch (McpProtocolAdmission.Rejected exception) {
            response.setStatus(exception.status());
            response.setContentType("application/json;charset=UTF-8");
            response.setHeader("Cache-Control", "no-store");
            if (exception.status() == 429) { response.setHeader("Retry-After", Integer.toString(exception.retryAfter())); }
            mapper.writeValue(response.getOutputStream(), Map.of("error",
                    exception.status() == 429 ? "rate_limit_exceeded" : "temporarily_unavailable"));
            return;
        }
        chain.doFilter(request, response);
    }
}
