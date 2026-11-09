package com.howe.mcp.controller;

import com.howe.common.exception.ServiceException;
import com.howe.common.exception.base.BaseException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 浏览器 API 使用独立 JSON 错误，避免被后台全局处理器包装为 AjaxResult。 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = {McpBrowserController.class, McpAuthorizationContextController.class,
        McpOAuthMetadataController.class, McpSelfController.class})
@ConditionalOnProperty(prefix = "howe.mcp", name = "installed", havingValue = "true")
public class McpBrowserExceptionHandler {

    /** 当前账号已失效时关闭相应 OAuth 会话，不影响管理端 JWT。 */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> inactive(HttpServletRequest request) {
        var session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return ResponseEntity.status(401).body(Map.of("error", "login_required", "message", "请重新登录"));
    }

    /** 所有认证失败使用稳定提示，不回显凭据或区分不存在的账号。 */
    @ExceptionHandler({ServiceException.class, BaseException.class})
    public ResponseEntity<Map<String, String>> loginFailure(RuntimeException exception) {
        if (exception instanceof ServiceException && "访问过于频繁，请稍候再试".equals(exception.getMessage())) {
            return ResponseEntity.status(429).body(Map.of("error", "rate_limited", "message", "访问过于频繁，请稍后再试"));
        }
        return ResponseEntity.badRequest().body(Map.of("error", "login_failed", "message", "登录失败，请检查账号和验证信息"));
    }

    /** 参数错误不包含字段拒绝值，防止密码进入错误响应。 */
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Map<String, String>> invalidInput() {
        return ResponseEntity.badRequest().body(Map.of("error", "invalid_request", "message", "请检查请求参数"));
    }

    /** 基础设施故障拒绝服务，不回显堆栈、密码或令牌。 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> unavailable() {
        log.warn("MCP 浏览器入口暂不可用");
        return ResponseEntity.status(503).body(Map.of("error", "temporarily_unavailable", "message", "服务暂不可用，请稍后再试"));
    }
}
