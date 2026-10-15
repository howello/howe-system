import request from '@/utils/request'
import type { ShareSourceQueryParams, ShareSource, WebDavEntry, AjaxResult, TableDataInfo } from '@/types'

/** 查询 WebDAV 源列表 */
export function listSource(query: ShareSourceQueryParams): Promise<TableDataInfo<ShareSource[]>> {
  return request({
    url: '/share/source/list',
    method: 'get',
    params: query
  })
}

/** 查询启用状态的源列表（下拉选择用） */
export function listEnabledSource(): Promise<AjaxResult<ShareSource[]>> {
  return request({
    url: '/share/source/enabled',
    method: 'get'
  })
}

/** 获取源详情 */
export function getSource(sourceId: number): Promise<AjaxResult<ShareSource>> {
  return request({
    url: '/share/source/' + sourceId,
    method: 'get'
  })
}

/** 新增源 */
export function addSource(data: ShareSource): Promise<AjaxResult> {
  return request({
    url: '/share/source',
    method: 'post',
    data: data
  })
}

/** 修改源 */
export function updateSource(data: ShareSource): Promise<AjaxResult> {
  return request({
    url: '/share/source',
    method: 'put',
    data: data
  })
}

/** 删除源 */
export function delSource(sourceId: number | number[]): Promise<AjaxResult> {
  return request({
    url: '/share/source/' + sourceId,
    method: 'delete'
  })
}

/** 浏览 WebDAV 目录 */
export function browseSource(sourceId: number, path?: string): Promise<AjaxResult<WebDavEntry[]>> {
  return request({
    url: '/share/source/browse',
    method: 'get',
    params: { sourceId, path }
  })
}

/** 测试源连通性 */
export function testSource(sourceId: number): Promise<AjaxResult<boolean>> {
  return request({
    url: '/share/source/test',
    method: 'get',
    params: { sourceId }
  })
}
