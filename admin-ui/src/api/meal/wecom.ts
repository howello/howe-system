import request from '@/utils/request'
import type { MealWeComConfig, MealWeComConfigQueryParams, AjaxResult, TableDataInfo } from '@/types'

// 查询企业微信通知配置列表（key 为掩码）
export function listWeComConfig(query: MealWeComConfigQueryParams): Promise<TableDataInfo<MealWeComConfig[]>> {
  return request({
    url: '/meal/wecom/list',
    method: 'get',
    params: query
  })
}

// 查询配置详情（key 为掩码）
export function getWeComConfig(configId: number): Promise<AjaxResult<MealWeComConfig>> {
  return request({
    url: '/meal/wecom/' + configId,
    method: 'get'
  })
}

// 新增配置
export function addWeComConfig(data: MealWeComConfig): Promise<AjaxResult> {
  return request({
    url: '/meal/wecom',
    method: 'post',
    data: data
  })
}

// 修改配置（webhookKey 留空或回传掩码表示保留原值）
export function updateWeComConfig(data: MealWeComConfig): Promise<AjaxResult> {
  return request({
    url: '/meal/wecom',
    method: 'put',
    data: data
  })
}

// 删除配置
export function delWeComConfig(configId: number | number[]): Promise<AjaxResult> {
  return request({
    url: '/meal/wecom/' + configId,
    method: 'delete'
  })
}

// 发送测试消息
export function testWeComConfig(configId: number): Promise<AjaxResult<string>> {
  return request({
    url: '/meal/wecom/' + configId + '/test',
    method: 'post'
  })
}
