import request from '@/utils/request'
import type { MealAppVersionQueryParams, MealAppVersion, WgtUploadResult, AjaxResult, TableDataInfo } from '@/types'

// 查询 App 版本列表
export function listAppVersion(query: MealAppVersionQueryParams): Promise<TableDataInfo<MealAppVersion[]>> {
  return request({
    url: '/meal/app/version/list',
    method: 'get',
    params: query
  })
}

// 查询 App 版本详情
export function getAppVersion(versionId: number): Promise<AjaxResult<MealAppVersion>> {
  return request({
    url: '/meal/app/version/' + versionId,
    method: 'get'
  })
}

// 新增 App 版本
export function addAppVersion(data: MealAppVersion): Promise<AjaxResult> {
  return request({
    url: '/meal/app/version',
    method: 'post',
    data: data
  })
}

// 修改 App 版本
export function updateAppVersion(data: MealAppVersion): Promise<AjaxResult> {
  return request({
    url: '/meal/app/version',
    method: 'put',
    data: data
  })
}

// 删除 App 版本
export function delAppVersion(versionId: number | number[]): Promise<AjaxResult> {
  return request({
    url: '/meal/app/version/' + versionId,
    method: 'delete'
  })
}

// 上传 wgt 资源包（存 R2），返回可访问地址
export function uploadAppVersionWgt(file: File): Promise<AjaxResult<WgtUploadResult>> {
  const data = new FormData()
  data.append('file', file)
  return request({
    url: '/meal/app/version/upload',
    method: 'post',
    data: data,
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
}
