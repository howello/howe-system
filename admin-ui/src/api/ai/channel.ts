import request from '@/utils/request'
import type { AiChannel, AiChannelQueryParams, AiProvider, AjaxResult, TableDataInfo } from '@/types'

// 查询渠道列表
export function listChannel(query: AiChannelQueryParams): Promise<TableDataInfo<AiChannel[]>> {
  return request({
    url: '/ai/channel/list',
    method: 'get',
    params: query
  })
}

// 查询渠道详情（密钥为掩码）
export function getChannel(id: number): Promise<AjaxResult<AiChannel>> {
  return request({
    url: '/ai/channel/' + id,
    method: 'get'
  })
}

// 查询服务商下拉选项
export function listProviderOptions(): Promise<AjaxResult<AiProvider[]>> {
  return request({
    url: '/ai/channel/providers',
    method: 'get'
  })
}

// 新增渠道
export function addChannel(data: AiChannel): Promise<AjaxResult> {
  return request({
    url: '/ai/channel',
    method: 'post',
    data: data
  })
}

// 修改渠道（apiKey 留空表示保留原值）
export function updateChannel(data: AiChannel): Promise<AjaxResult> {
  return request({
    url: '/ai/channel',
    method: 'put',
    data: data
  })
}

// 删除渠道
export function delChannel(id: number | number[]): Promise<AjaxResult> {
  return request({
    url: '/ai/channel/' + id,
    method: 'delete'
  })
}

// 启用或停用渠道
export function changeChannelStatus(id: number, enabled: number): Promise<AjaxResult> {
  return request({
    url: '/ai/channel/' + id + '/status',
    method: 'put',
    params: { enabled }
  })
}

// 测试渠道连通性
export function testChannel(id: number, modelId?: number): Promise<AjaxResult> {
  return request({
    url: '/ai/channel/' + id + '/test',
    method: 'post',
    params: modelId ? { modelId } : {}
  })
}
