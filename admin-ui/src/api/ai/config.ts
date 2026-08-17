import request from '@/utils/request'
import type { AjaxResult } from '@/types'
import type { AiConfig, AiConfigPayload, AiConfigResource, AiKeyReplacement } from '@/types/api/ai'

// 配置列表（按 resource 区分表）
export function listConfig(resource: AiConfigResource, keyword?: string, pageNum = 1, pageSize = 10): Promise<AjaxResult<AiConfig[]>> {
  return request({ url: `/ai/admin/config/${resource}`, method: 'get', params: { keyword, pageNum, pageSize } })
}

// 配置详情
export function getConfig(resource: AiConfigResource, id: number): Promise<AjaxResult<AiConfig>> {
  return request({ url: `/ai/admin/config/${resource}/${id}`, method: 'get' })
}

// 创建配置
export function addConfig(resource: AiConfigResource, data: AiConfigPayload): Promise<AjaxResult<AiConfig>> {
  return request({ url: `/ai/admin/config/${resource}`, method: 'post', data })
}

// 更新配置
export function updateConfig(resource: AiConfigResource, id: number, data: AiConfigPayload): Promise<AjaxResult<AiConfig>> {
  return request({ url: `/ai/admin/config/${resource}/${id}`, method: 'put', data })
}

// 替换 API Key（仅返回脱敏摘要与版本，绝不回显明文）
export function replaceApiKey(resource: AiConfigResource, id: number, data: AiConfigPayload): Promise<AjaxResult<AiKeyReplacement>> {
  return request({ url: `/ai/admin/config/${resource}/${id}/key`, method: 'put', data })
}

// 测试连通性（运行时执行，此接口返回占位提示）
export function testConfig(resource: AiConfigResource, id: number): Promise<AjaxResult<{ ok: boolean; message: string }>> {
  return request({ url: `/ai/admin/config/${resource}/${id}/test`, method: 'post' })
}

// 启停配置
export function toggleConfig(resource: AiConfigResource, id: number, enabled: boolean): Promise<AjaxResult<number>> {
  return request({ url: `/ai/admin/config/${resource}/${id}/status`, method: 'put', params: { enabled } })
}

// 停用配置
export function disableConfig(resource: AiConfigResource, id: number): Promise<AjaxResult<number>> {
  return request({ url: `/ai/admin/config/${resource}/${id}/disable`, method: 'put' })
}

// 删除配置
export function delConfig(resource: AiConfigResource, id: number): Promise<AjaxResult<number>> {
  return request({ url: `/ai/admin/config/${resource}/${id}`, method: 'delete' })
}
