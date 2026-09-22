import request from '@/utils/request'
import type { MealReviewQueryParams, MealReview, AjaxResult, TableDataInfo } from '@/types'

// 查询评价列表
export function listReview(query: MealReviewQueryParams): Promise<TableDataInfo<MealReview[]>> {
  return request({
    url: '/meal/review/list',
    method: 'get',
    params: query
  })
}

// 删除评价
export function delReview(reviewId: number | number[]): Promise<AjaxResult> {
  return request({
    url: '/meal/review/' + reviewId,
    method: 'delete'
  })
}
