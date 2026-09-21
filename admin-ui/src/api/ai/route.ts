import request from '@/utils/request'
import type { AiSceneRoute, AiSceneRouteQueryParams, AjaxResult, TableDataInfo } from '@/types'

// 查询场景路由列表
export function listRoute(query: AiSceneRouteQueryParams): Promise<TableDataInfo<AiSceneRoute[]>> {
  return request({
    url: '/ai/route/list',
    method: 'get',
    params: query
  })
}

// 新增或修改场景路由
export function saveRoute(data: AiSceneRoute): Promise<AjaxResult> {
  return request({
    url: '/ai/route',
    method: 'post',
    data: data
  })
}

// 删除场景路由
export function delRoute(id: number | number[]): Promise<AjaxResult> {
  return request({
    url: '/ai/route/' + id,
    method: 'delete'
  })
}
