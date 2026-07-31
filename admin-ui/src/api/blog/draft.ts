import request from '@/utils/request'
import type { BlogDraftQueryParams, BlogDraft, AjaxResult, TableDataInfo } from '@/types'

// 查询草稿列表
export function listDraft(query: BlogDraftQueryParams): Promise<TableDataInfo<BlogDraft[]>> {
  return request({
    url: '/blog/draft/list',
    method: 'get',
    params: query
  })
}

// 查询草稿详情
export function getDraft(draftId: number): Promise<AjaxResult<BlogDraft>> {
  return request({
    url: '/blog/draft/' + draftId,
    method: 'get'
  })
}

// 新增草稿
export function addDraft(data: BlogDraft): Promise<AjaxResult> {
  return request({
    url: '/blog/draft',
    method: 'post',
    data: data
  })
}

// 修改草稿
export function updateDraft(data: BlogDraft): Promise<AjaxResult> {
  return request({
    url: '/blog/draft',
    method: 'put',
    data: data
  })
}

// 删除草稿
export function delDraft(draftId: number | number[]): Promise<AjaxResult> {
  return request({
    url: '/blog/draft/' + draftId,
    method: 'delete'
  })
}

// 发布草稿：生成 markdown 提交到 GitHub
export function publishDraft(draftId: number, filePath?: string): Promise<AjaxResult> {
  return request({
    url: '/blog/draft/publish/' + draftId,
    method: 'post',
    params: { filePath },
    // 提交要走 GitHub API，比普通接口慢
    timeout: 60000
  })
}
