import request from '@/utils/request'
import type {
  ShareLinkQueryParams,
  ShareLink,
  ShareLinkCreateRequest,
  ShareLinkUpdateRequest,
  ShareAccessLog,
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
