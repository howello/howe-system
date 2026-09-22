import request from '@/utils/request'
import type { MealOrderQueryParams, MealOrder, AjaxResult, TableDataInfo } from '@/types'

// 查询订单列表
export function listOrder(query: MealOrderQueryParams): Promise<TableDataInfo<MealOrder[]>> {
  return request({
    url: '/meal/order/list',
    method: 'get',
    params: query
  })
}

// 查询订单详情（含点餐明细）
export function getOrder(orderId: number): Promise<AjaxResult<MealOrder>> {
  return request({
    url: '/meal/order/' + orderId,
    method: 'get'
  })
}

// 接单
export function acceptOrder(orderId: number): Promise<AjaxResult> {
  return request({
    url: '/meal/order/accept/' + orderId,
    method: 'put'
  })
}

// 标记完成
export function finishOrder(orderId: number): Promise<AjaxResult> {
  return request({
    url: '/meal/order/finish/' + orderId,
    method: 'put'
  })
}

// 删除订单
export function delOrder(orderId: number | number[]): Promise<AjaxResult> {
  return request({
    url: '/meal/order/' + orderId,
    method: 'delete'
  })
}
