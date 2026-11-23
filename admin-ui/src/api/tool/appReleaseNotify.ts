import request from '@/utils/request'
import type { AjaxResult } from '@/types'
import type { AppReleaseKey } from '@/types/api/meal/appversion'

export interface AppReleaseNotifyConfig {
  configId: number
  label: string
  enabled: boolean
  maskedKey: string
}

export interface AppReleaseNotifyForm {
  label: string
  enabled: boolean
  webhookKey: string
}

function configUrl(appKey: AppReleaseKey) {
  return `/tool/app-release/notify/${encodeURIComponent(appKey)}`
}

export function listReleaseNotifyConfigs(appKey: AppReleaseKey): Promise<AjaxResult<AppReleaseNotifyConfig[]>> {
  return request({ url: configUrl(appKey), method: 'get' })
}

export function saveReleaseNotifyConfig(appKey: AppReleaseKey, data: AppReleaseNotifyForm, configId?: number): Promise<AjaxResult> {
  const url = configUrl(appKey)
  return configId === undefined
    ? request({ url, method: 'post', data })
    : request({ url: `${url}/${configId}`, method: 'put', data })
}

export function deleteReleaseNotifyConfig(appKey: AppReleaseKey, configId: number): Promise<AjaxResult> {
  return request({ url: `${configUrl(appKey)}/${configId}`, method: 'delete' })
}
