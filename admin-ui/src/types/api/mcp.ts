/** MCP 独立页面公开配置，仅包含当前生效地址。 */
export interface McpPublicConfiguration {
  installed: boolean
  enabled: boolean
  backendBaseUrl?: string
  resource?: string
}
/** 独立浏览器会话 CSRF 契约。 */
export interface McpCsrf { csrfToken: string; csrfHeaderName: string; csrfParameterName: string }
/** 服务端校验后的授权确认信息。 */
export interface McpConsent { state: string; clientId: string; clientName: string; redirectUri: string; scopes: string[]; submitUrl: string }
/** 本人连接公开信息。 */
export interface McpConnection {
  grantId: number; clientId: string; clientName: string; registrationSource: string
  resource: string; status: string; clientStatus: string; scopes: string[]
  createdAt: string; updatedAt: string; lastUsedAt: string | null; revokedAt: string | null
}
/** 本人调用元数据，不包含请求和响应正文。 */
export interface McpCall { logId: number; requestId: string; eventType: string; toolName: string | null; resultCode: string; elapsedMs: number; createdAt: string }
/** 自助接口分页结构。 */
export interface McpPage<T> { items: T[]; total: number; page: number; pageSize: number }
