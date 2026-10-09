import request from '@/utils/request'
import type { MealDishQueryParams, MealDish, MealDishAiCurrent, MealDishAiResult, AjaxResult, TableDataInfo } from '@/types'

// 查询菜品列表
export function listDish(query: MealDishQueryParams): Promise<TableDataInfo<MealDish[]>> {
  return request({
    url: '/meal/dish/list',
    method: 'get',
    params: query
  })
}

// 查询菜品详情
export function getDish(dishId: number): Promise<AjaxResult<MealDish>> {
  return request({
    url: '/meal/dish/' + dishId,
    method: 'get'
  })
}

// 新增菜品
export function addDish(data: MealDish): Promise<AjaxResult> {
  return request({
    url: '/meal/dish',
    method: 'post',
    data: data
  })
}

// 修改菜品
export function updateDish(data: MealDish): Promise<AjaxResult> {
  return request({
    url: '/meal/dish',
    method: 'put',
    data: data
  })
}

// 一键 AI 补齐菜品信息（只补缺失字段，不覆盖已填）
export function aiGenerateDish(data: {
  name: string
  deptId?: number
  current?: MealDishAiCurrent
}): Promise<AjaxResult<MealDishAiResult>> {
  return request({
    url: '/meal/dish/ai-generate',
    method: 'post',
    data,
    timeout: 120000
  })
}

// 删除菜品
export function delDish(dishId: number | number[]): Promise<AjaxResult> {
  return request({
    url: '/meal/dish/' + dishId,
    method: 'delete'
  })
}
