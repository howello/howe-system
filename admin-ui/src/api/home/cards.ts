import request from '@/utils/request'
import type {
  AjaxResult,
  HomeCards,
  HomeWelcome,
  HomeTodoItem,
  HomeFootprint,
  MealHomeStats,
  MealDishOverview,
  MealAppVersionOverview,
  MealHotDishes,
  AiHomeStats,
  AiChannelHealth,
  AiImageAssetOverview,
  ShareHomeStats,
  ShareCacheHealth,
  BlogLinkSyncOverview
} from '@/types'

// 首页可见卡片清单（后端按权限与模块可用性下发）
export function getHomeCards(): Promise<AjaxResult<HomeCards>> {
  return request({
    url: '/home/cards',
    method: 'get'
  })
}

// 欢迎卡信息（昵称、角色、上次登录）
export function getHomeWelcome(): Promise<AjaxResult<HomeWelcome>> {
  return request({
    url: '/home/welcome',
    method: 'get'
  })
}

// 待办聚合（仅返回有权限处理的事项）
export function getHomeTodos(): Promise<AjaxResult<HomeTodoItem[]>> {
  return request({
    url: '/home/todos',
    method: 'get'
  })
}

// 个人足迹
export function getHomeFootprint(): Promise<AjaxResult<HomeFootprint>> {
  return request({
    url: '/home/footprint',
    method: 'get'
  })
}

// ---------- 模块层：点餐 ----------

// 点餐统计
export function getMealStats(): Promise<AjaxResult<MealHomeStats>> {
  return request({ url: '/meal/stats/summary', method: 'get' })
}

// 菜品概况
export function getMealDishOverview(): Promise<AjaxResult<MealDishOverview>> {
  return request({ url: '/meal/stats/dish', method: 'get' })
}

// App 版本概况
export function getMealAppVersionOverview(): Promise<AjaxResult<MealAppVersionOverview>> {
  return request({ url: '/meal/stats/appVersion', method: 'get' })
}

// 热门菜品
export function getMealHotDishes(): Promise<AjaxResult<MealHotDishes>> {
  return request({ url: '/meal/stats/hotDishes', method: 'get' })
}

// ---------- 模块层：AI ----------

// AI 用量
export function getAiStats(): Promise<AjaxResult<AiHomeStats>> {
  return request({ url: '/ai/stats/summary', method: 'get' })
}

// AI 渠道健康
export function getAiChannelHealth(): Promise<AjaxResult<AiChannelHealth>> {
  return request({ url: '/ai/stats/channelHealth', method: 'get' })
}

// AI 图片资产概况
export function getAiImageAssets(): Promise<AjaxResult<AiImageAssetOverview>> {
  return request({ url: '/ai/stats/imageAssets', method: 'get' })
}

// ---------- 模块层：分享 ----------

// 分享统计
export function getShareStats(): Promise<AjaxResult<ShareHomeStats>> {
  return request({ url: '/share/stats/summary', method: 'get' })
}

// 分享缓存健康
export function getShareCacheHealth(): Promise<AjaxResult<ShareCacheHealth>> {
  return request({ url: '/share/stats/cacheHealth', method: 'get' })
}

// ---------- 模块层：博客友链同步 ----------

// 友链与订阅同步概况
export function getBlogLinkSync(): Promise<AjaxResult<BlogLinkSyncOverview>> {
  return request({ url: '/blog/stats/linkSync', method: 'get' })
}
