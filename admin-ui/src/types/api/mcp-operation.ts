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

/** 操作详情；summary 只含服务端白名单字段。 */
export interface McpOperationView {
  operationId: string
  toolName?: string
  targetType?: string
  targetId?: string | null
  expectedVersion?: string | null
  state: McpOperationState
  stage?: string | null
  summary?: Record<string, unknown> | null
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
