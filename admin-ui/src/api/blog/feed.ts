import request from '@/utils/request'
import type {
  BlogFeedQueryParams,
  BlogFeedItemQueryParams,
  BlogFeedItem,
  BlogFeedSyncResult,
  BlogLink,
  AjaxResult,
  TableDataInfo
} from '@/types'

// 查询订阅源列表（订阅源与友链共用 BlogLink 实体，后端按 link_type 隔离）
export function listFeed(query: BlogFeedQueryParams): Promise<TableDataInfo<BlogLink[]>> {
  return request({
    url: '/blog/feed/list',
    method: 'get',
    params: query
  })
}

// 查询订阅源详情
export function getFeed(linkId: number): Promise<AjaxResult<BlogLink>> {
  return request({
    url: '/blog/feed/' + linkId,
    method: 'get'
  })
}

// 新增订阅源
export function addFeed(data: BlogLink): Promise<AjaxResult> {
  return request({
    url: '/blog/feed',
    method: 'post',
    data: data
  })
}

// 修改订阅源
export function updateFeed(data: BlogLink): Promise<AjaxResult> {
  return request({
    url: '/blog/feed',
    method: 'put',
    data: data
  })
}

// 删除订阅源（连带删除其条目）
export function delFeed(linkId: number | number[]): Promise<AjaxResult> {
  return request({
    url: '/blog/feed/' + linkId,
    method: 'delete'
  })
}

// 同步全部启用中的订阅源
export function syncAllFeeds(): Promise<AjaxResult<BlogFeedSyncResult>> {
  return request({
    url: '/blog/feed/sync',
    method: 'post',
    // 要逐个抓取第三方站点，比普通接口慢得多
    timeout: 120000
  })
}

// 同步单个订阅源
export function syncOneFeed(linkId: number): Promise<AjaxResult<BlogFeedSyncResult>> {
  return request({
    url: '/blog/feed/sync/' + linkId,
    method: 'post',
    timeout: 60000
  })
}

// 查询朋友圈条目列表，linkId 可选（主从联动）
export function listFeedItems(query: BlogFeedItemQueryParams): Promise<TableDataInfo<BlogFeedItem[]>> {
  return request({
    url: '/blog/feed/item/list',
    method: 'get',
    params: query
  })
}

// 删除朋友圈条目
export function delFeedItem(itemIds: number | number[]): Promise<AjaxResult> {
  return request({
    url: '/blog/feed/item/' + itemIds,
    method: 'delete'
  })
}
