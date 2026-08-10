import request from '@/utils/request'
import type { AjaxResult, HomeStats } from '@/types'

// 首页博客聚合统计（含 blogAvailable 降级标志）
export function getHomeStats(): Promise<AjaxResult<HomeStats>> {
  return request({
    url: '/home/stats/summary',
    method: 'get'
  })
}