package com.howe.mcp.operation;

import com.howe.common.annotation.RateLimiter;
import com.howe.common.enums.LimitType;
import com.howe.common.tool.exception.ToolException;
import com.howe.mcp.controller.McpBrowserController;
import com.howe.mcp.service.spi.McpAccountBridge;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * MCP 写操作浏览器确认入口。
 *
 * <p>落在 `/oauth2/self/**` 浏览器安全链内：只认 OAuth Cookie 会话与 CSRF，
 * 不接受管理端 JWT 或 MCP Bearer 直接确认。所有操作按会话账号归属查询，
 * 不存在与越权统一返回同一错误。</p>
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "MCP 写操作确认")
@ConditionalOnProperty(prefix = "howe.mcp", name = "installed", havingValue = "true")
public class McpOperationController {

    private final McpOperationService operations;
    private final McpOperationBindingService bindings;
    private final McpOperationStepStore stepStore;
    private final McpAccountBridge accounts;

    private final com.howe.mcp.service.security.McpExecutionIdentity identity;

    private final com.howe.mcp.service.McpToolPolicyService policy;

    private final org.springframework.beans.factory.ObjectProvider<com.howe.mcp.service.spi.McpToolExecutionBridge> bridges;

    /** 分页查询本人操作，列表不返回正文与短链。 */
    @RateLimiter(count = 120, time = 60, limitType = LimitType.IP)
    @Operation(summary = "分页查询本人 MCP 写操作")
    @GetMapping("/oauth2/self/operations")
    public ResponseEntity<Map<String, Object>> list(Authentication authentication,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String state) {
        Long userId = currentUser(authentication);
        if (page < 1 || page > 10000 || pageSize < 1 || pageSize > 50) {
            return badRequest();
        }
        return noStore(operations.page(userId, page, pageSize, state));
    }

    /** 读取本人确认详情；只返回服务端记录，不采用 URL 中的摘要。 */
    @RateLimiter(count = 120, time = 60, limitType = LimitType.IP)
    @Operation(summary = "读取 MCP 写操作详情")
    @GetMapping("/oauth2/self/operations/{id}")
    public ResponseEntity<Map<String, Object>> view(Authentication authentication, @PathVariable String id) {
        Long userId = currentUser(authentication);
        try {
            Map<String, Object> view = operations.confirmationView(id, userId, binding(userId, id));
            // 步骤状态沿用同一记录归属校验，不暴露载荷或回执。
            operations.putSteps(view, stepStore.findByOperation(id));
            return noStore(view);
        } catch (ToolException exception) {
            return rejected(exception);
        }
    }

    /** 创建确认上下文；期限固定，不因刷新延长。 */
    @RateLimiter(count = 60, time = 60, limitType = LimitType.IP)
    @Operation(summary = "创建 MCP 写操作确认上下文")
    @PostMapping("/oauth2/self/operations/{id}/confirmation-context")
    public ResponseEntity<Map<String, Object>> context(Authentication authentication, @PathVariable String id,
            @RequestBody ContextRequest body, jakarta.servlet.http.HttpServletRequest request) {
        Long userId = currentUser(authentication);
        try {
            McpOperationRecord record = bindings.record(userId, id);
            McpOperationBinding binding = bindings.binding(record);
            return noStore(authorized(record, () -> operations.openConfirmation(id, userId, binding, body.rowVersion(),
                    bindings.summaryHash(record), McpOperationSession.hash(request, authentication, id))));
        } catch (ToolException exception) {
            return rejected(exception);
        }
    }

    /** 本人确认并原子入队；凭证只在服务端内部生成与消费。 */
    @RateLimiter(count = 30, time = 60, limitType = LimitType.IP)
    @Operation(summary = "确认 MCP 写操作")
    @PostMapping("/oauth2/self/operations/{id}/confirm")
    public ResponseEntity<Map<String, Object>> confirm(Authentication authentication, @PathVariable String id,
            @RequestBody ConfirmRequest body, jakarta.servlet.http.HttpServletRequest request) {
        Long userId = currentUser(authentication);
        try {
            McpOperationRecord record = bindings.record(userId, id);
            return noStore(authorized(record, () -> operations.confirm(id, userId, bindings.binding(record), body.rowVersion(),
                    body.summaryHash(), McpOperationSession.hash(request, authentication, id), Instant.now())));
        } catch (ToolException exception) {
            return rejected(exception);
        }
    }

    /** 本人拒绝；不产生业务效果。 */
    @RateLimiter(count = 30, time = 60, limitType = LimitType.IP)
    @Operation(summary = "拒绝 MCP 写操作")
    @PostMapping("/oauth2/self/operations/{id}/reject")
    public ResponseEntity<Map<String, Object>> reject(Authentication authentication, @PathVariable String id,
            @RequestBody RejectRequest body) {
        Long userId = currentUser(authentication);
        try {
            McpOperationRecord record = bindings.record(userId, id);
            if (record.rowVersion() != body.rowVersion()) {
                throw new ToolException("state_conflict", "操作状态已变化");
            }
            return noStore(operations.reject(id, userId, bindings.binding(record), body.rowVersion()));
        } catch (ToolException exception) {
            return rejected(exception);
        }
    }

    /** 浏览器确认与工具执行复用当前身份、权限和数据范围，领域不读取 OAuth 会话主体。 */
    private <T> T authorized(McpOperationRecord record, java.util.function.Supplier<T> action) {
        McpOperationBinding binding = bindings.binding(record);
        bindings.requireToolEnabled(record);
        operations.requireScope(record, binding.scopes());
        var invocation = new com.howe.mcp.service.security.McpInvocationContext(record.userId(), null,
                record.preparedDeptId(), bindings.externalClientId(record), record.grantId(), record.grantVersion(),
                binding.scopes(), record.resourceUri(), "browser-confirm-" + record.operationId());
        return identity.execute(invocation, () -> {
            var access = operations.accesses().get(record.toolName() + "@" + record.toolVersion());
            var bridge = bridges.getIfAvailable();
            if (access == null || bridge == null || !policy.allowed(access)) {
                throw new ToolException("execution_denied", "当前账号无权确认原工具操作");
            }
            if (!java.util.Objects.equals(com.howe.common.utils.SecurityUtils.getDeptId(), record.preparedDeptId())) {
                throw new ToolException("authorization_changed", "家庭归属已变化，请重新准备");
            }
            return bridge.execute(access, action);
        });
    }

    /** 每个请求重新加载当前正常账号。 */
    private Long currentUser(Authentication authentication) {
        Long userId = McpBrowserController.userId(authentication);
        accounts.loadActiveUser(userId);
        return userId;
    }

    /** 按操作反查原授权绑定，供服务层复核。 */
    private McpOperationBinding binding(Long userId, String operationId) {
        return bindings.binding(bindings.record(userId, operationId));
    }

    /** no-store。 */
    private static ResponseEntity<Map<String, Object>> noStore(Map<String, Object> body) {
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(body);
    }

    /** 固定业务拒绝。 */
    private static ResponseEntity<Map<String, Object>> rejected(ToolException exception) {
        String code = exception.code() == null ? "request_rejected" : exception.code();
        int status = "not_found_or_forbidden".equals(code) ? 404 : 409;
        return ResponseEntity.status(status).header("Cache-Control", "no-store")
                .body(Map.of("error", code, "message", exception.getMessage()));
    }

    /** 分页错误。 */
    private static ResponseEntity<Map<String, Object>> badRequest() {
        return ResponseEntity.badRequest().header("Cache-Control", "no-store")
                .body(Map.of("error", "invalid_request"));
    }

    /** 上下文请求。 */
    public record ContextRequest(long rowVersion) {
    }

    /** 确认请求。 */
    public record ConfirmRequest(long rowVersion, String summaryHash) {
    }

    /** 拒绝请求。 */
    public record RejectRequest(long rowVersion) {
    }
}
