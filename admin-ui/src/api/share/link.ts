import request from '@/utils/request'
import type {
  ShareLinkQueryParams,
  ShareLink,
  ShareLinkCreateRequest,
  ShareLinkUpdateRequest,
  ShareAccessLog,
  ShareUploadResult,
  ShareRemoteCheckResult,
  AjaxResult,
  TableDataInfo
} from '@/types'

/** 查询分享列表 */
export function listLink(query: ShareLinkQueryParams): Promise<TableDataInfo<ShareLink[]>> {
  return request({
    url: '/share/link/list',
    method: 'get',
    params: query
  })
}

/** 获取分享详情 */
export function getLink(linkId: number): Promise<AjaxResult<ShareLink>> {
  return request({
    url: '/share/link/' + linkId,
    method: 'get'
  })
}

/** 创建分享 */
export function addLink(data: ShareLinkCreateRequest): Promise<AjaxResult<ShareLink>> {
  return request({
    url: '/share/link',
    method: 'post',
    data: data
  })
}

/** 修改分享 */
export function updateLink(data: ShareLinkUpdateRequest): Promise<AjaxResult> {
  return request({
    url: '/share/link',
    method: 'put',
    data: data
  })
}

/** 手动失效 / 恢复 */
export function changeLinkStatus(linkId: number, status: string): Promise<AjaxResult> {
  return request({
    url: '/share/link/status',
    method: 'put',
    params: { linkId, status }
  })
}

/** 删除分享 */
export function delLink(linkId: number | number[]): Promise<AjaxResult> {
  return request({
    url: '/share/link/' + linkId,
    method: 'delete'
  })
}

/** 手动重新缓存 */
export function recacheLink(linkId: number): Promise<AjaxResult> {
  return request({
    url: '/share/link/recache/' + linkId,
    method: 'post'
  })
}

/** 查询分享访问记录 */
export function listLinkLogs(linkId: number): Promise<AjaxResult<ShareAccessLog[]>> {
  return request({
    url: '/share/link/logs/' + linkId,
    method: 'get'
  })
}

/** 本地上传暂存：返回一次性令牌，创建分享时用 uploadToken 认领 */
export function uploadShareFile(file: File): Promise<AjaxResult<ShareUploadResult>> {
  const data = new FormData()
  data.append('file', file)
  return request({
    url: '/share/link/upload',
    method: 'post',
    data,
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 120000
  })
}

/** 校验远程链接可达性；结果仅供即时反馈，创建时服务端会再校验 */
export function checkShareRemote(url: string): Promise<AjaxResult<ShareRemoteCheckResult>> {
  return request({
    url: '/share/link/check-remote',
    method: 'post',
    data: { url },
    timeout: 60000
  })
}
