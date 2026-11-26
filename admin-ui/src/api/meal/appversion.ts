import request from '@/utils/request'
import type { MealAppVersionQueryParams, MealAppVersion, WgtUploadResult, AppPackageCheckResult, AjaxResult, TableDataInfo } from '@/types'
import type { AppReleaseKey } from '@/types/api/meal/appversion'

// 查询 App 版本列表
export function listAppVersion(query: MealAppVersionQueryParams): Promise<TableDataInfo<MealAppVersion[]>> {
  return request({
    url: '/app/version/list',
    method: 'get',
    params: query
  })
}

// 查询 App 版本详情
export function getAppVersion(versionId: number): Promise<AjaxResult<MealAppVersion>> {
  return request({
    url: '/app/version/' + versionId,
    method: 'get'
  })
}

// 新增 App 版本
export function addAppVersion(data: MealAppVersion): Promise<AjaxResult> {
  return request({
    url: '/app/version',
    method: 'post',
    data: data
  })
}

// 修改 App 版本
export function updateAppVersion(data: MealAppVersion): Promise<AjaxResult> {
  return request({
    url: '/app/version',
    method: 'put',
    data: data
  })
}

// 删除 App 版本
export function delAppVersion(versionId: number | number[]): Promise<AjaxResult> {
  return request({
    url: '/app/version/' + versionId,
    method: 'delete'
  })
}

// 上传 wgt 资源包（存 R2），返回可访问地址
export function uploadAppVersionWgt(file: File, appKey: AppReleaseKey = 'meal'): Promise<AjaxResult<WgtUploadResult>> {
  const data = new FormData()
  data.append('file', file)
  data.append('appKey', appKey)
  return request({
    url: '/app/version/upload',
    method: 'post',
    data: data,
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
}

// 校验远程 wgt 链接（有界下载 + wgt 结构验证）；前端超时放宽到 60s 覆盖后端预算
export function checkAppPackage(url: string, appKey: AppReleaseKey = 'meal'): Promise<AjaxResult<AppPackageCheckResult>> {
  return request({
    url: '/app/version/check-package',
    params: { appKey },
    method: 'post',
    data: { url },
    timeout: 60000
  })
}
