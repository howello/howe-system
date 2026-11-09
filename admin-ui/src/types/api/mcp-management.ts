import type { PageDomain } from './common'
/** 后台白名单查询条件，不提供自定义 SQL 排序。 */
export interface McpAdminQuery extends PageDomain { keyword?: string; status?: string; userId?: number; toolName?: string; resultCode?: string }
/** 不包含客户端认证秘密的登记信息。 */
export interface McpAdminClient {
  id: string; clientId: string; clientName: string; registrationSource: string; status: string
  metadataValid: string; grantCount: number; createdAt: string; updatedAt: string; lastUsedAt: string | null
}
/** 只读协议元数据，管理端不能修改回调地址。 */
export interface McpAdminClientDetail { client: McpAdminClient; redirectUris: string[]; authenticationMethods: string[]; grantTypes: string[]; scopes: string[] }
/** 代码中真实注册的工具契约。 */
export interface McpAdminTool {
  schema: { name: string; title?: string; description?: string; inputSchema: unknown; annotations?: Record<string, unknown> }
  version: string; scope: string; permissions: string[]; timeoutMs: number; maxResultBytes: number
  enabled: string; updateBy: string; updatedAt: string | null; remark: string
}
/** 授权状态，不含授权载荷或令牌。 */
export interface McpAdminGrant {
  grantId: number; userId: number; clientId: string; clientName: string; resource: string
  status: string; clientStatus: string; authorizationVersion: number; createdAt: string; updatedAt: string; revokedAt: string | null
}
/** 日志查询仅有固定元数据。 */
export interface McpAdminCall {
  logId: number; requestId: string; eventType: string; userId: number | null; grantId: number | null
  clientId: string | null; clientName: string | null; toolName: string | null; resultCode: string; elapsedMs: number; createdAt: string
}

/** 经过公开站点校验的地址，非法原始配置不会回显。 */
export interface McpAdminAddresses { issuer: string; resource: string; frontendBaseUrl: string }
/** 在线保存值不自动替换协议快照。 */
export interface McpAdminConfiguration { enabled: boolean; active: McpAdminAddresses; configured: McpAdminAddresses | null; configuredValid: boolean; restartRequired: boolean }
/** 当前进程启动以来的审计计数与执行容量，不是集群统计。 */
export interface McpAdminStatistics {
  audit: { submitted: number; written: number; failed: number; dropped: number; queued: number; active: number }
  execution: { limit: number; perUserLimit: number; running: number; activeUsers: number; closed: boolean }
}
