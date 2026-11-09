package com.howe.mcp.service.security;

import lombok.Getter;

/** 自动登记使用标准 OAuth 错误与 HTTP 状态，不携带原始请求或凭据。 */
@Getter
public final class McpRegistrationException extends RuntimeException {
    private final String error;
    private final int status;

    /** 错误说明仅由服务端固定文本构造。 */
    public McpRegistrationException(String error, int status, String description) {
        super(description);
        this.error = error;
        this.status = status;
    }
}
