import request from '@/utils/request'
import type { BlogTalkQueryParams, BlogTalk, AjaxResult, TableDataInfo } from '@/types'

// 查询说说列表
export function listTalk(query: BlogTalkQueryParams): Promise<TableDataInfo<BlogTalk[]>> {
  return request({
    url: '/blog/talk/list',
    method: 'get',
    params: query
  })
}

// 查询说说详情
export function getTalk(talkId: number): Promise<AjaxResult<BlogTalk>> {
  return request({
    url: '/blog/talk/' + talkId,
    method: 'get'
  })
}

// 新增说说
export function addTalk(data: BlogTalk): Promise<AjaxResult> {
  return request({
    url: '/blog/talk',
    method: 'post',
    data: data
  })
}

// 修改说说
export function updateTalk(data: BlogTalk): Promise<AjaxResult> {
  return request({
    url: '/blog/talk',
    method: 'put',
    data: data
  })
}

// 删除说说
export function delTalk(talkId: number | number[]): Promise<AjaxResult> {
  return request({
    url: '/blog/talk/' + talkId,
    method: 'delete'
  })
}
