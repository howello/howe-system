import request from '@/utils/request'
import type { BlogArticleQueryParams, BlogArticle, BlogSyncResult, AjaxResult, TableDataInfo } from '@/types'

// 查询文章列表
export function listArticle(query: BlogArticleQueryParams): Promise<TableDataInfo<BlogArticle[]>> {
  return request({
    url: '/blog/article/list',
    method: 'get',
    params: query
  })
}

// 查询文章详情（正文实时从 GitHub 拉取）
export function getArticle(articleId: number): Promise<AjaxResult<BlogArticle>> {
  return request({
    url: '/blog/article/' + articleId,
    method: 'get'
  })
}

// 新增文章
export function addArticle(data: BlogArticle): Promise<AjaxResult> {
  return request({
    url: '/blog/article',
    method: 'post',
    data: data
  })
}

// 修改文章
export function updateArticle(data: BlogArticle): Promise<AjaxResult> {
  return request({
    url: '/blog/article',
    method: 'put',
    data: data
  })
}

// 删除文章（同时删除仓库中的 markdown 文件）
export function delArticle(articleId: number | number[]): Promise<AjaxResult> {
  return request({
    url: '/blog/article/' + articleId,
    method: 'delete'
  })
}

// 从 GitHub 全量重建索引
export function syncArticle(): Promise<AjaxResult<BlogSyncResult>> {
  return request({
    url: '/blog/article/sync',
    method: 'post',
    // 同步要逐个读文件，耗时远超默认的 10 秒
    timeout: 300000
  })
}
