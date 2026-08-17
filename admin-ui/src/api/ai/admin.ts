import request from '@/utils/request'
import type {
  AjaxResult,
} from '@/types'
import type {
  AgentDraftPayload,
  AiAgent,
  AiAgentVersion,
  AiRun,
  AiRunEvent,
  AiModelCall,
  AiToolCall,
  ConversationCreatePayload,
  AiConversation,
  AiConversationQueryParams,
} from '@/types/api/ai'

// ===== Agent 草稿与版本 =====

// 创建 Agent 草稿
export function addAgent(data: AgentDraftPayload): Promise<AjaxResult<number>> {
  return request({ url: '/ai/admin/agents', method: 'post', data })
}

// 更新 Agent 草稿（乐观锁：expectedVersion 为当前草稿版本号）
export function updateAgent(agentId: number, expectedVersion: number, data: AgentDraftPayload): Promise<AjaxResult<number>> {
  return request({ url: `/ai/admin/agents/${agentId}?expectedVersion=${expectedVersion}`, method: 'put', data })
}

// 查询 Agent 草稿详情
export function getAgent(agentId: number): Promise<AjaxResult<AiAgent>> {
  return request({ url: '/ai/admin/agents/' + agentId, method: 'get' })
}

// 校验 Agent 草稿（不落库，仅返回校验结果或抛业务错误）
export function validateAgent(data: AgentDraftPayload): Promise<AjaxResult<void>> {
  return request({ url: '/ai/admin/agents/validate', method: 'post', data })
}

// 发布 Agent 版本
export function publishAgent(agentId: number, versionNo: number): Promise<AjaxResult<number>> {
  return request({ url: `/ai/admin/agents/${agentId}/publish?versionNo=${versionNo}`, method: 'post' })
}

// 停用 Agent
export function disableAgent(agentId: number): Promise<AjaxResult<number>> {
  return request({ url: `/ai/admin/agents/${agentId}/disable`, method: 'post' })
}

// 查询 Agent 版本列表
export function listAgentVersions(agentId: number, pageNum = 1, pageSize = 10): Promise<AjaxResult<AiAgentVersion[]>> {
  return request({ url: `/ai/admin/agents/${agentId}/versions`, method: 'get', params: { pageNum, pageSize } })
}

// 查询 Agent 版本详情（不可变版本只读）
export function getAgentVersion(agentId: number, versionNo: number): Promise<AjaxResult<AiAgentVersion>> {
  return request({ url: `/ai/admin/agents/${agentId}/versions/${versionNo}`, method: 'get' })
}

// ===== 会话 =====

// 创建会话（绑定一个已发布且未停用的 Agent）
export function createConversation(data: ConversationCreatePayload): Promise<AjaxResult<number>> {
  return request({ url: '/ai/admin/conversations', method: 'post', data })
}

// 查询当前用户的会话列表（服务端按登录用户过滤）
export function listConversations(query: AiConversationQueryParams): Promise<AjaxResult<AiConversation[]>> {
  return request({ url: '/ai/admin/conversations', method: 'get', params: query })
}

// ===== 运行与事件 =====

// 发送消息（入队，返回 runId；幂等键由客户端生成，重复发送返回同一 runId）
export function enqueueMessage(conversationId: number, content: string, idempotencyKey: string): Promise<AjaxResult<number>> {
  return request({
    url: `/ai/admin/conversations/${conversationId}/messages`,
    method: 'post',
    params: { content, idempotencyKey },
  })
}

// 查询运行
export function getRun(runId: number): Promise<AjaxResult<AiRun>> {
  return request({ url: '/ai/admin/runs/' + runId, method: 'get' })
}

// 取消运行（进入 CANCEL_REQUESTED，终态请求幂等）
export function cancelRun(runId: number): Promise<AjaxResult<number>> {
  return request({ url: `/ai/admin/runs/${runId}/cancel`, method: 'post' })
}

// 查询运行事件（分页，历史回放）
export function listRunEvents(runId: number, pageNum = 1, pageSize = 10): Promise<AjaxResult<AiRunEvent[]>> {
  return request({ url: `/ai/admin/runs/${runId}/events`, method: 'get', params: { pageNum, pageSize } })
}

// 查询模型用量（成本明细）
export function listRunUsage(runId: number, pageNum = 1, pageSize = 10): Promise<AjaxResult<AiModelCall[]>> {
  return request({ url: `/ai/admin/runs/${runId}/usage`, method: 'get', params: { pageNum, pageSize } })
}

// 查询工具用量
export function listRunToolUsage(runId: number, pageNum = 1, pageSize = 10): Promise<AjaxResult<AiToolCall[]>> {
  return request({ url: `/ai/admin/runs/${runId}/tool-usage`, method: 'get', params: { pageNum, pageSize } })
}
