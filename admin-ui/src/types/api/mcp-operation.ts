/** MCP 写操作状态。 */
export type McpOperationState =
  | 'PREVIEW' | 'PREPARED' | 'CONFIRMED' | 'QUEUED' | 'RUNNING'
  | 'SUCCEEDED' | 'FAILED' | 'UNKNOWN' | 'CANCELLED' | 'EXPIRED'

/** 后续步骤视图；只含固定代码键、状态与结果码。 */
export interface McpOperationStepView {
  stepKey: string
  state: string
  stage?: string | null
  resultCode?: string | null
  createTime?: string | null
  updateTime?: string | null
}

/** 确认详情；summary 只含服务端白名单字段。 */
export interface McpOperationView {
  operationId: string
  toolName?: string
  targetType?: string
  targetId?: string | null
  expectedVersion?: string | null
  state: McpOperationState
  stage?: string | null
  rowVersion?: number
  prepareExpiresAt?: string | null
  confirmationExpiresAt?: string | null
  canConfirm?: boolean
  summary?: Record<string, unknown> | null
  summaryHash?: string
  resultCode?: string | null
  createdAt?: string | null
  finishedAt?: string | null
  safeResult?: Record<string, unknown> | null
  retryAfterMs?: number | null
  steps?: McpOperationStepView[] | null
}

/** 操作列表分页。 */
export interface McpOperationPage {
  items: McpOperationView[]
  total: number
  page: number
  pageSize: number
}

/** 确认上下文响应。 */
export interface McpOperationContext {
  operationId: string
  summaryHash: string
  confirmationExpiresAt: string
  rowVersion: number
}
