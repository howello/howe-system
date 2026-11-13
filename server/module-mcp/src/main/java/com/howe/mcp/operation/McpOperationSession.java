package com.howe.mcp.operation;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;

/**
 * 浏览器确认会话绑定。
 *
 * <p>把当前 OAuth 浏览器会话主键、账号与操作标识一起摘要，防止同一账号在另一会话
 * 直接确认；不保存会话内容、凭证或任何原始 Cookie。</p>
 */
public final class McpOperationSession {

    /** 工具类不允许实例化。 */
    private McpOperationSession() {
    }

    /**
     * 计算会话绑定摘要。
     *
     * @param request 当前 HTTP 请求，可为 {@code null}
     * @param authentication 已认证主体，可为 {@code null}
     * @param operationId 操作标识
     * @return 32 字节摘要
     */
    public static byte[] hash(HttpServletRequest request, Authentication authentication, String operationId) {
        String sessionId = request == null || request.getSession(false) == null
                ? "" : request.getSession(false).getId();
        String principal = authentication == null ? "" : authentication.getName();
        return McpOperationStoreImpl.sha256(principal + "|" + sessionId + "|" + operationId);
    }
}
