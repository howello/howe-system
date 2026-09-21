import request from '@/utils/request'
import type { AiCallLog, AiCallLogQueryParams, AiCallLogStats, AiCallLogStatsParams, AjaxResult, TableDataInfo } from '@/types'

// 查询调用记录列表
export function listCallLog(query: AiCallLogQueryParams): Promise<TableDataInfo<AiCallLog[]>> {
  return request({
    url: '/ai/calllog/list',
    method: 'get',
    params: query
  })
}

// 查询调用记录详情
export function getCallLog(id: number): Promise<AjaxResult<AiCallLog>> {
  return request({
    url: '/ai/calllog/' + id,
    method: 'get'
  })
}

// 用量统计
export function getCallLogStats(query: AiCallLogStatsParams): Promise<AjaxResult<AiCallLogStats[]>> {
  return request({
    url: '/ai/calllog/stats',
    method: 'get',
    params: query
  })
}
