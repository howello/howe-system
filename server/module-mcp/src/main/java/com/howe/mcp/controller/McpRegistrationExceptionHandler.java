package com.howe.mcp.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.howe.mcp.service.security.McpRegistrationException;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 动态登记采用 OAuth JSON 错误，不暴露原始元数据、凭据或数据库堆栈。 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = McpClientRegistrationController.class)
@ConditionalOnProperty(prefix = "howe.mcp", name = "installed", havingValue = "true")
public class McpRegistrationExceptionHandler {

    /** 可预期的策略拒绝保持标准错误码，限流响应提供重试提示。 */
    @ExceptionHandler(McpRegistrationException.class)
    public ResponseEntity<Map<String, String>> rejected(McpRegistrationException exception) {
        var builder = ResponseEntity.status(exception.getStatus()).header("Cache-Control", "no-store");
        if (exception.getStatus() == 429) {
            builder.header("Retry-After", "60");
        }
        return builder.body(Map.of("error", exception.getError(), "error_description", exception.getMessage()));
    }

    /** JSON 解析错误不回显请求片段。 */
    @ExceptionHandler(JsonProcessingException.class)
    public ResponseEntity<Map<String, String>> malformed() {
        return ResponseEntity.badRequest().body(Map.of("error", "invalid_client_metadata", "error_description", "登记文档不是有效 JSON"));
    }

    /** 基础设施异常安全关闭，错误说明不含 secret 或 SQL。 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> unavailable() {
        log.warn("MCP 客户端登记暂不可用");
        return ResponseEntity.status(503).body(Map.of("error", "temporarily_unavailable", "error_description", "客户端登记暂不可用"));
    }
}
