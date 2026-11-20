/** MCP 独立页面公开配置，仅包含当前生效地址。 */
export interface McpPublicConfiguration {
  installed: boolean
  enabled: boolean
  backendBaseUrl?: string
  resource?: string
  loginPage?: string
  connectionsPage?: string
  operationsPage?: string
}
/** 独立浏览器会话 CSRF 契约。 */
export interface McpCsrf { csrfToken: string; csrfHeaderName: string; csrfParameterName: string; clientName?: string; clientId?: string }
/** 本人连接公开信息。 */
export interface McpConnection {
  grantId: number; clientId: string; clientName: string; registrationSource: string
  resource: string; status: string; clientStatus: string
  createdAt: string; updatedAt: string; revokedAt: string | null
}
/** 自助接口分页结构。 */
export interface McpPage<T> { items: T[]; total: number; page: number; pageSize: number }
