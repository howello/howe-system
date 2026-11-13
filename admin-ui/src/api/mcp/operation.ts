import type { McpOperationContext, McpOperationPage, McpOperationView } from '@/types/api/mcp-operation'
import type { McpBrowserClient } from './browser'

/**
 * MCP 写操作的浏览器 API。
 *
 * <p>全部经由现有 OAuth Cookie 客户端发送：携带 credentials、CSRF、no-store，
 * 不读取管理端 JWT。错误只暴露固定码，不回显服务端正文。</p>
 */
export class McpOperationClient {
  /** 注入已建立 Cookie 会话与 CSRF 的浏览器客户端。 */
  constructor(private readonly browser: McpBrowserClient) {
  }

  /** 分页查询本人操作。 */
  list(page: number, state?: string): Promise<McpOperationPage> {
    const query = state ? `&state=${encodeURIComponent(state)}` : ''
    return this.browser.request(`/oauth2/self/operations?page=${page}&pageSize=20${query}`)
  }

  /** 读取确认详情；只接受 operationId。 */
  view(operationId: string): Promise<McpOperationView> {
    return this.browser.request(`/oauth2/self/operations/${encodeURIComponent(operationId)}`)
  }

  /** 创建确认上下文；期限由服务端固定。 */
  context(operationId: string, rowVersion: number): Promise<McpOperationContext> {
    return this.browser.request(`/oauth2/self/operations/${encodeURIComponent(operationId)}/confirmation-context`, { rowVersion })
  }

  /** 提交确认；请求体只含行版本与服务端摘要哈希。 */
  confirm(operationId: string, rowVersion: number, summaryHash: string): Promise<McpOperationView> {
    return this.browser.request(`/oauth2/self/operations/${encodeURIComponent(operationId)}/confirm`, { rowVersion, summaryHash })
  }

  /** 拒绝操作。 */
  reject(operationId: string, rowVersion: number): Promise<McpOperationView> {
    return this.browser.request(`/oauth2/self/operations/${encodeURIComponent(operationId)}/reject`, { rowVersion })
  }
}
