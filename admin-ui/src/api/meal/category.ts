import request from '@/utils/request'
import type {
  MealCategoryQueryParams,
  MealCategory,
  MealCategorySaveRequest,
  MealCategorySaveResult,
  AjaxResult,
  TableDataInfo
} from '@/types'

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

// 新增分类：经 AI 审阅，AI 调用可能超过请求层默认超时
export function addCategory(data: MealCategorySaveRequest): Promise<AjaxResult<MealCategorySaveResult>> {
  return request({
    url: '/meal/category',
    method: 'post',
    data: data,
    timeout: 30000
  })
}

// 修改分类：经 AI 审阅
export function updateCategory(
  categoryId: number,
  data: MealCategorySaveRequest
): Promise<AjaxResult<MealCategorySaveResult>> {
  return request({
    url: '/meal/category/' + categoryId,
    method: 'put',
    data: data,
    timeout: 30000
  })
}

// 删除分类
export function delCategory(categoryId: number | number[]): Promise<AjaxResult> {
  return request({
    url: '/meal/category/' + categoryId,
    method: 'delete'
  })
}
