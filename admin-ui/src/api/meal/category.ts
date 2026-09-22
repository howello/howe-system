import request from '@/utils/request'
import type { MealCategoryQueryParams, MealCategory, AjaxResult, TableDataInfo } from '@/types'

// 查询分类列表
export function listCategory(query: MealCategoryQueryParams): Promise<TableDataInfo<MealCategory[]>> {
  return request({
    url: '/meal/category/list',
    method: 'get',
    params: query
  })
}

// 查询分类详情
export function getCategory(categoryId: number): Promise<AjaxResult<MealCategory>> {
  return request({
    url: '/meal/category/' + categoryId,
    method: 'get'
  })
}

// 新增分类
export function addCategory(data: MealCategory): Promise<AjaxResult> {
  return request({
    url: '/meal/category',
    method: 'post',
    data: data
  })
}

// 修改分类
export function updateCategory(data: MealCategory): Promise<AjaxResult> {
  return request({
    url: '/meal/category',
    method: 'put',
    data: data
  })
}

// 删除分类
export function delCategory(categoryId: number | number[]): Promise<AjaxResult> {
  return request({
    url: '/meal/category/' + categoryId,
    method: 'delete'
  })
}
