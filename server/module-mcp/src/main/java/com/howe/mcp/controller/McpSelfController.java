package com.howe.mcp.controller;

import com.howe.common.annotation.RateLimiter;
import com.howe.common.enums.LimitType;
import com.howe.mcp.service.McpGrantService;
import com.howe.mcp.service.McpSelfService;
import com.howe.mcp.service.spi.McpAccountBridge;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 所有正常账号可用的自助连接入口，不依赖后台角色、菜单或 JWT。 */
@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "MCP 我的连接与调用")
@ConditionalOnProperty(prefix = "howe.mcp", name = "installed", havingValue = "true")
public class McpSelfController {
    private final McpAccountBridge accounts;
    private final McpSelfService self;
    private final McpGrantService grants;

    /** 列表始终使用 Session 中的本人主键，运行开关关闭时仍可查询。 */
    @RateLimiter(count = 120, time = 60, limitType = LimitType.IP)
    @Operation(summary = "分页查询本人的 MCP 连接")
    @GetMapping("/oauth2/self/connections")
    public ResponseEntity<?> connections(Authentication authentication, @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        Long userId = currentUser(authentication);
        try {
            return ResponseEntity.ok().header("Cache-Control", "no-store").body(self.connections(userId, page, pageSize));
        } catch (IllegalArgumentException exception) {
            return invalid();
        }
    }

    /** 用户主动撤销整个 grant，必须通过本人的 OAuth Session 与 CSRF。 */
    @RateLimiter(count = 20, time = 60, limitType = LimitType.IP)
    @Operation(summary = "撤销本人的 MCP 连接")
    @PostMapping("/oauth2/self/connections/{id}/revoke")
    public ResponseEntity<Map<String, Object>> revoke(Authentication authentication, @PathVariable Long id) {
        Long userId = currentUser(authentication);
        try {
            grants.revokeOwned(userId, id);
            return ResponseEntity.ok().header("Cache-Control", "no-store").body(Map.of("revoked", true));
        } catch (AccessDeniedException exception) {
            // 归属不符与账号失效分开，不能因一次越权尝试注销仍正常的用户会话。
            return ResponseEntity.status(403).header("Cache-Control", "no-store").body(Map.of("error", "request_rejected"));
        }
    }

    /** 查询本人的调用元数据，不接收 userId，也不公开任何原始协议报文。 */
    @RateLimiter(count = 120, time = 60, limitType = LimitType.IP)
    @Operation(summary = "分页查询本人的 MCP 调用记录")
    @GetMapping("/oauth2/self/calls")
    public ResponseEntity<?> calls(Authentication authentication, @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        Long userId = currentUser(authentication);
        try {
            return ResponseEntity.ok().header("Cache-Control", "no-store").body(self.calls(userId, page, pageSize));
        } catch (IllegalArgumentException exception) {
            return invalid();
        }
    }

    /** 每个自助操作先刷新当前正常账号，不能只依赖登录时状态。 */
    private Long currentUser(Authentication authentication) {
        Long userId = McpBrowserController.userId(authentication);
        accounts.loadActiveUser(userId);
        return userId;
    }

    /** 分页错误采用独立 JSON，不返回堆栈或后台 AjaxResult。 */
    private static ResponseEntity<Map<String, String>> invalid() {
        return ResponseEntity.badRequest().header("Cache-Control", "no-store").body(Map.of("error", "invalid_request"));
    }
}
