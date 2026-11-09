package com.howe.mcp.controller;

import cn.hutool.core.io.IoUtil;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.howe.common.annotation.Anonymous;
import com.howe.mcp.service.security.McpRegistrationException;
import com.howe.mcp.dto.McpClientMetadata;
import com.howe.mcp.service.McpClientRegistrationService;
import com.howe.mcp.service.McpRegistrationAdmission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** RFC 7591 客户端自动登记；无登录 Cookie 或后台 JWT 依赖。 */
@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "MCP OAuth 客户端自动登记")
@ConditionalOnProperty(prefix = "howe.mcp", name = "installed", havingValue = "true")
public class McpClientRegistrationController {
    private static final int MAX_BYTES = 16384;
    private final McpClientRegistrationService clients;
    private final McpRegistrationAdmission admission;
    private final ObjectMapper mapper;

    /** 有界读取 JSON，包括没有 Content-Length 的请求；重复键和尾随文档均拒绝。 */
    @Anonymous
    @Operation(summary = "动态登记外部 MCP 客户端")
    @PostMapping(value = "/oauth2/register")
    public ResponseEntity<Map<String, Object>> register(HttpServletRequest request) throws java.io.IOException {
        clients.requireEnabled();
        admission.acquire(request.getRemoteAddr());
        try {
            MediaType type = request.getContentType() == null ? null : MediaType.parseMediaType(request.getContentType());
            if (type == null || !"application".equals(type.getType()) || !"json".equals(type.getSubtype())) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException exception) {
            throw new McpRegistrationException("invalid_client_metadata", 400, "登记请求必须使用 JSON");
        }
        if (request.getContentLengthLong() > MAX_BYTES) {
            throw new McpRegistrationException("invalid_client_metadata", 413, "登记文档超过大小限制");
        }
        byte[] body = IoUtil.readBytes(request.getInputStream(), MAX_BYTES + 1);
        if (body.length > MAX_BYTES) {
            throw new McpRegistrationException("invalid_client_metadata", 413, "登记文档超过大小限制");
        }
        var document = mapper.reader().with(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(body);
        return ResponseEntity.status(201).contentType(MediaType.APPLICATION_JSON).header("Cache-Control", "no-store").header("Pragma", "no-cache")
                .body(clients.register(McpClientMetadata.parse(document)));
    }
}
