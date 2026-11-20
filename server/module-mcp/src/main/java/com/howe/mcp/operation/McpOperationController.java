package com.howe.mcp.operation;

import com.howe.common.annotation.RateLimiter;
import com.howe.common.enums.LimitType;
import com.howe.common.tool.exception.ToolException;
import com.howe.mcp.controller.McpBrowserController;
import com.howe.mcp.service.spi.McpAccountBridge;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * MCP 写操作浏览器查询入口。
 *
 * <p>落在 `/oauth2/self/**` 浏览器安全链内：只认 OAuth Cookie 会话与 CSRF，
 * 不接受管理端 JWT 或 MCP Bearer 直接查询。所有操作按会话账号归属查询，
 * 不存在与越权统一返回同一错误。</p>
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "MCP 写操作")
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

    /** 读取本人操作详情；只返回服务端记录，不采用 URL 中的摘要。 */
    @RateLimiter(count = 120, time = 60, limitType = LimitType.IP)
    @Operation(summary = "读取 MCP 写操作详情")
    @GetMapping("/oauth2/self/operations/{id}")
    public ResponseEntity<Map<String, Object>> view(Authentication authentication, @PathVariable String id) {
        Long userId = currentUser(authentication);
        try {
            McpOperationRecord record = bindings.record(userId, id);
            Map<String, Object> view = authorized(record, () -> {
                Map<String, Object> detail = McpOperationService.view(record);
                var plan = operations.plan(record);
                var handler = operations.handlers().get(record.toolName() + "@" + record.toolVersion());
                if (handler == null) {
                    throw new ToolException("tool_unavailable", "原工具不可用");
                }
                var context = new com.howe.common.tool.model.ToolExecutionContext(userId.toString(),
                        record.preparedDeptId() == null ? null : record.preparedDeptId().toString(),
                        "browser-operation-" + record.operationId());
                handler.revalidate(new com.howe.common.tool.operation.ToolOperationContext(context,
                        com.howe.common.tool.operation.ToolOperationContext.Phase.RESULT_READ,
                        record.operationId(), record.fence(), java.time.Instant.now(), null, () -> { }), plan);
                detail.put("summary", plan.safeSummary());
                return detail;
            });
            // 步骤状态沿用同一记录归属校验，不暴露载荷或回执。
            operations.putSteps(view, stepStore.findByOperation(id));
            return noStore(view);
        } catch (ToolException exception) {
            return rejected(exception);
        }
    }

    /** 浏览器查询与工具执行复用当前身份、权限和数据范围，领域不读取 OAuth 会话主体。 */
    private <T> T authorized(McpOperationRecord record, java.util.function.Supplier<T> action) {
        McpOperationBinding binding = bindings.resultBinding(record);
        var invocation = new com.howe.mcp.service.security.McpInvocationContext(record.userId(), null,
                record.preparedDeptId(), bindings.externalClientId(record), record.grantId(), binding.grantVersion(),
                binding.scopes(), record.resourceUri(), "browser-operation-" + record.operationId());
        return identity.execute(invocation, () -> {
            var access = operations.accesses().get(record.toolName() + "@" + record.toolVersion());
            var bridge = bridges.getIfAvailable();
            if (access == null || bridge == null || !policy.allowed(access)) {
                throw new ToolException("execution_denied", "当前账号无权读取原工具操作");
            }
            if (!java.util.Objects.equals(com.howe.common.utils.SecurityUtils.getDeptId(), record.preparedDeptId())) {
                throw new ToolException("authorization_changed", "家庭归属已变化");
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

}
