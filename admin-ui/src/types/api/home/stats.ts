/** 首页博客统计-今日计数 */
export interface HomeStatsToday {
  articles: number
  drafts: number
  talks: number
}

/** 首页博客统计-30 天趋势点 */
export interface HomeTrendPoint {
  date: string
  articles: number
  drafts: number
  talks: number
}

/** 首页博客统计-分类分布 */
export interface HomeCategoryCount {
  name: string
  count: number
}

/** 首页博客统计-聚合块 */
export interface HomeStatsBlog {
  articleTotal: number
  draftTotal: number
  talkTotal: number
  today: HomeStatsToday
  trend: HomeTrendPoint[]
  categoryCounts: HomeCategoryCount[]
}

/** 首页博客统计-顶层响应 data（blogAvailable=false 时 blog 为 null） */
export interface HomeStats {
  blogAvailable: boolean
  blog: HomeStatsBlog | null
}