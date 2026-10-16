/**
 * 首页工作台：卡片清单与各卡片统计数据
 */

/** 卡片标识（与后端卡片目录、前端组件注册表一一对应） */
export type HomeCardKey =
  // 固定层
  | 'F1' | 'F2' | 'F3' | 'N1' | 'N4'
  // 角色层
  | 'R1' | 'R2' | 'R3'
  // 模块层
  | 'M1' | 'M14' | 'M15' | 'N13'
  | 'M2' | 'N9' | 'N10' | 'N11'
  | 'M3' | 'N5' | 'N8'
  | 'M4' | 'N16'

/** 卡片清单响应 */
export interface HomeCards {
  cards: HomeCardKey[]
}

/** 欢迎卡信息 */
export interface HomeWelcome {
  nickName: string | null
  roleNames: string[]
  lastLoginTime: string | null
  lastLoginIp: string | null
}

/** 待办项 */
export interface HomeTodoItem {
  label: string
  count: number
  path: string
}

/** 个人足迹（模块不可用的项为 null） */
export interface HomeFootprint {
  articles: number | null
  orders: number | null
  proposals: number | null
  links: number | null
}

/** 公告（带当前用户已读标记） */
export interface HomeNotice {
  noticeId: number
  noticeTitle: string
  noticeContent?: string
  noticeType?: string
  createTime?: string
  isRead: boolean
}

/** 公告列表响应（附带未读数） */
export interface HomeNoticeResult {
  code: number
  msg: string
  rows?: HomeNotice[]
  data?: HomeNotice[]
  unreadCount?: number
}

// ---------- 模块层：点餐 ----------

/** 点餐趋势点 */
export interface MealTrendPoint {
  date: string
  count: number
}

/** 点餐统计 */
export interface MealHomeStats {
  todayOrders: number
  pendingCount: number
  cookingCount: number
  doneCount: number
  avgScore: number
  trend: MealTrendPoint[]
}

/** 菜品概况 */
export interface MealDishOverview {
  onShelfCount: number
  offShelfCount: number
  categoryCount: number
  publicCount: number
  familyCount: number
}

/** App 版本概况 */
export interface MealAppVersionOverview {
  latestVersionName: string | null
  latestPublishTime: string | null
  forceUpdate: boolean
  totalCount: number
}

/** 热门菜品 */
export interface MealHotDish {
  dishName: string
  count: number
}

/** 热门菜品列表 */
export interface MealHotDishes {
  dishes: MealHotDish[]
}

// ---------- 模块层：AI ----------

/** AI 场景用量 */
export interface AiSceneUsage {
  scene: string
  count: number
}

/** AI 用量统计 */
export interface AiHomeStats {
  todayCalls: number
  todayTokens: number
  successRate: number
  sceneBreakdown: AiSceneUsage[]
}

/** AI 渠道状态 */
export interface AiChannelStatus {
  name: string
  health: string
}

/** AI 渠道健康 */
export interface AiChannelHealth {
  channels: AiChannelStatus[]
  downCount: number
  disabledCount: number
  lastCheckTime: string | null
}

/** AI 图片资产概况 */
export interface AiImageAssetOverview {
  totalCount: number
  totalBytes: number
  latestTime: string | null
}

// ---------- 模块层：分享 ----------

/** 最近创建的链接 */
export interface ShareRecentLink {
  fileName: string
  createTime: string
}

/** 分享统计 */
export interface ShareHomeStats {
  linkTotal: number
  todayViews: number
  todayDownloads: number
  recentLinks: ShareRecentLink[]
}

/** 分享缓存健康 */
export interface ShareCacheHealth {
  readyCount: number
  pendingCount: number
  failedCount: number
}

// ---------- 模块层：博客友链同步 ----------

/** 订阅同步失败条目 */
export interface BlogFeedFailure {
  linkName: string
  lastError: string
}

/** 友链与订阅同步概况 */
export interface BlogLinkSyncOverview {
  feedTotal: number
  failedCount: number
  lastSyncTime: string | null
  failures: BlogFeedFailure[]
}
