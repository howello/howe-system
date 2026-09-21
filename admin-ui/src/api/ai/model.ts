import request from '@/utils/request'
import type { AiModel, AiModelImportRequest, AiModelQueryParams, AiRemoteModel, AjaxResult, TableDataInfo } from '@/types'

// 查询模型列表
export function listModel(query: AiModelQueryParams): Promise<TableDataInfo<AiModel[]>> {
  return request({
    url: '/ai/model/list',
    method: 'get',
    params: query
  })
}

// 查询模型详情
export function getModel(id: number): Promise<AjaxResult<AiModel>> {
  return request({
    url: '/ai/model/' + id,
    method: 'get'
  })
}

// 新增模型
export function addModel(data: AiModel): Promise<AjaxResult> {
  return request({
    url: '/ai/model',
    method: 'post',
    data: data
  })
}

// 修改模型
export function updateModel(data: AiModel): Promise<AjaxResult> {
  return request({
    url: '/ai/model',
    method: 'put',
    data: data
  })
}

// 删除模型
export function delModel(id: number | number[]): Promise<AjaxResult> {
  return request({
    url: '/ai/model/' + id,
    method: 'delete'
  })
}

// 启用或停用模型
export function changeModelStatus(id: number, enabled: number): Promise<AjaxResult> {
  return request({
    url: '/ai/model/' + id + '/status',
    method: 'put',
    params: { enabled }
  })
}

// 查询渠道对应厂商的模型列表
export function listRemoteModel(channelId: number): Promise<AjaxResult<AiRemoteModel[]>> {
  return request({
    url: '/ai/model/remote',
    method: 'get',
    params: { channelId }
  })
}

// 批量导入厂商模型
export function batchAddModel(data: AiModelImportRequest): Promise<AjaxResult> {
  return request({
    url: '/ai/model/batch',
    method: 'post',
    data: data
  })
}
