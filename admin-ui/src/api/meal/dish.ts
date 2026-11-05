import request from '@/utils/request'
import type {
  MealDishQueryParams,
  MealDish,
  MealDishAiCurrent,
  MealDishAiTextResult,
  MealDishAiCoverResult,
  DishOwnerOption,
  AjaxResult,
  TableDataInfo
} from '@/types'

// 查询菜品列表
export function listDish(query: MealDishQueryParams): Promise<TableDataInfo<MealDish[]>> {
  return request({
    url: '/meal/dish/list',
    method: 'get',
    params: query
  })
}

// 查询可调整归属的家庭（仅平台管理员）
export function listDishOwnerOptions(keyword?: string): Promise<AjaxResult<DishOwnerOption[]>> {
  return request({
    url: '/meal/dish/ownerOptions',
    method: 'get',
    params: keyword ? { keyword } : {}
  })
}

// 调整菜品归属（仅平台管理员；expectedDeptId 为旧归属，用于并发校验）
export function changeDishOwner(dishId: number, data: { targetDeptId: number; expectedDeptId: number }): Promise<AjaxResult> {
  return request({
    url: '/meal/dish/' + dishId + '/owner',
    method: 'put',
    data
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

// 一键 AI 补齐菜品文本信息（只补缺失字段，不覆盖已填）
export function aiGenerateDishText(data: {
  name: string
  deptId?: number
  current?: MealDishAiCurrent
}): Promise<AjaxResult<MealDishAiTextResult>> {
  return request({
    url: '/meal/dish/ai-generate/text',
    method: 'post',
    data,
    timeout: 120000
  })
}

// 一键 AI 生成菜品封面（已填封面时不生成；传 forceRegenerate + imageDescription 时强制重建）
export function aiGenerateDishCover(data: {
  name: string
  deptId?: number
  current?: MealDishAiCurrent
  /** 画面描述，强制重建时后端要求必填 */
  imageDescription?: string
  /** 强制重新生成，忽略「已有封面就跳过」的默认行为 */
  forceRegenerate?: boolean
}): Promise<AjaxResult<MealDishAiCoverResult>> {
  return request({
    url: '/meal/dish/ai-generate/cover',
    method: 'post',
    data,
    timeout: 120000
  })
}

// 删除菜品
/** 查询本人生成图片的补偿转存结果。 */
export function getImageTransferTask(taskId: string): Promise<AjaxResult<{ state: 'READY' | 'PENDING_TRANSFER' | 'FAILED'; message: string }>> {
  return request({ url: '/ai/image-transfer/' + encodeURIComponent(taskId), method: 'get' })
}

export function delDish(dishId: number | number[]): Promise<AjaxResult> {
  return request({
    url: '/meal/dish/' + dishId,
    method: 'delete'
  })
}
