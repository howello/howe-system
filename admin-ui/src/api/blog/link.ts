import request from '@/utils/request'
import type { BlogLinkQueryParams, BlogLink, AjaxResult, TableDataInfo } from '@/types'

// 查询友链列表
export function listLink(query: BlogLinkQueryParams): Promise<TableDataInfo<BlogLink[]>> {
  return request({
    url: '/blog/link/list',
    method: 'get',
    params: query
  })
}

// 查询友链详情
export function getLink(linkId: number): Promise<AjaxResult<BlogLink>> {
  return request({
    url: '/blog/link/' + linkId,
    method: 'get'
  })
}

// 新增友链
export function addLink(data: BlogLink): Promise<AjaxResult> {
  return request({
    url: '/blog/link',
    method: 'post',
    data: data
  })
}

// 修改友链
export function updateLink(data: BlogLink): Promise<AjaxResult> {
  return request({
    url: '/blog/link',
    method: 'put',
    data: data
  })
}

// 删除友链
export function delLink(linkId: number | number[]): Promise<AjaxResult> {
  return request({
    url: '/blog/link/' + linkId,
    method: 'delete'
  })
}
