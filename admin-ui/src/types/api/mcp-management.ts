import type { PageDomain } from './common'
/** 后台白名单查询条件，不提供自定义 SQL 排序。 */
export interface McpAdminQuery extends PageDomain { keyword?: string; status?: string; userId?: number }
/** 不包含客户端认证秘密的登记信息。 */
export interface McpAdminClient {
  id: string; clientId: string; clientName: string; registrationSource: string; status: string
  metadataValid: string; grantCount: number; createdAt: string; updatedAt: string; lastUsedAt: string | null
}
/** 只读协议元数据，管理端不能修改回调地址。 */
export interface McpAdminClientDetail { client: McpAdminClient; redirectUris: string[]; authenticationMethods: string[]; grantTypes: string[]; scopes: string[] }
/** 授权状态，不含授权载荷或令牌。 */
export interface McpAdminGrant {
  grantId: number; userId: number; clientId: string; clientName: string; resource: string
  status: string; clientStatus: string; authorizationVersion: number; createdAt: string; updatedAt: string; revokedAt: string | null
}
/** 经过公开站点校验的地址，非法原始配置不会回显。 */
export interface McpAdminAddresses { issuer: string; resource: string; frontendBaseUrl: string }
/** 在线保存值不自动替换协议快照。 */
export interface McpAdminConfiguration { enabled: boolean; active: McpAdminAddresses; configured: McpAdminAddresses | null; configuredValid: boolean; restartRequired: boolean }
