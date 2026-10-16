/**
 * 首页卡片注册表：卡片标识 → 组件 + 布局占宽
 *
 * 后端 /home/cards 只下发「哪些卡片可见、按什么顺序」，UI 实现（组件与占宽）
 * 属于前端职责，集中在此表。新增卡片时只改这里与卡片目录，首页组装逻辑不动。
 *
 * 清单中出现但此表没有的标识会被安全跳过（后端先于前端上线新卡片时不会白屏）。
 */
import type { HomeCardKey } from '@/types'

/** 卡片注册项 */
export interface CardRegistration {
  /** 卡片组件（懒加载：未出现在清单中的卡片不会加载其代码） */
  component: unknown
  /** 栅格占宽（12 列栅格） */
  span: number
  /** 纵向跨行数（默认 1）；用于「左侧大卡跨两行、右侧两卡上下排列」的布局 */
  rowSpan?: number
}

/** 卡片注册表 */
export const CARD_REGISTRY: Record<HomeCardKey, CardRegistration> = {
  // ---------- 固定层 ----------
  F1: { component: defineAsyncComponent(() => import('./components/cards/WelcomeCard.vue')), span: 12 },
  F2: { component: defineAsyncComponent(() => import('./components/cards/NoticeCard.vue')), span: 4 },
  F3: { component: defineAsyncComponent(() => import('./components/cards/TodoCard.vue')), span: 4 },
  N1: { component: defineAsyncComponent(() => import('./components/cards/QuickEntryCard.vue')), span: 4 },
  N4: { component: defineAsyncComponent(() => import('./components/cards/FootprintCard.vue')), span: 12 },

  // ---------- 角色层 ----------
  // 布局：左侧服务器资源占半行（跨两行高），右侧系统监控与缓存监控上下排列
  R1: { component: defineAsyncComponent(() => import('./components/cards/ServerResourceCard.vue')), span: 6, rowSpan: 2 },
  R2: { component: defineAsyncComponent(() => import('./components/cards/MonitorCard.vue')), span: 6 },
  R3: { component: defineAsyncComponent(() => import('./components/cards/CacheCard.vue')), span: 6 },

  // ---------- 模块层：博客 ----------
  // 两行布局：第一行「博客统计 + 友链」，第二行「博客趋势 + 博客分类」（每张占半行）
  M1: { component: defineAsyncComponent(() => import('./components/cards/BlogStatsCard.vue')), span: 6 },
  N13: { component: defineAsyncComponent(() => import('./components/cards/BlogLinkCard.vue')), span: 6 },
  M14: { component: defineAsyncComponent(() => import('./components/cards/BlogTrendCard.vue')), span: 6 },
  M15: { component: defineAsyncComponent(() => import('./components/cards/BlogCategoryCard.vue')), span: 6 },

  // ---------- 模块层：点餐 ----------
  M2: { component: defineAsyncComponent(() => import('./components/cards/MealStatsCard.vue')), span: 8 },
  N9: { component: defineAsyncComponent(() => import('./components/cards/MealDishCard.vue')), span: 4 },
  N10: { component: defineAsyncComponent(() => import('./components/cards/MealAppVersionCard.vue')), span: 4 },
  N11: { component: defineAsyncComponent(() => import('./components/cards/MealHotDishCard.vue')), span: 8 },

  // ---------- 模块层：AI ----------
  M3: { component: defineAsyncComponent(() => import('./components/cards/AiStatsCard.vue')), span: 8 },
  N5: { component: defineAsyncComponent(() => import('./components/cards/AiChannelCard.vue')), span: 4 },
  N8: { component: defineAsyncComponent(() => import('./components/cards/AiImageCard.vue')), span: 4 },

  // ---------- 模块层：分享 ----------
  M4: { component: defineAsyncComponent(() => import('./components/cards/ShareStatsCard.vue')), span: 4 },
  N16: { component: defineAsyncComponent(() => import('./components/cards/ShareCacheCard.vue')), span: 4 }
}

/**
 * 取卡片注册项；未注册的标识返回 null（由调用方安全跳过）
 *
 * @param key 卡片标识
 * @returns 注册项；未注册时返回 null
 */
export function resolveCard(key: string): CardRegistration | null {
  return (CARD_REGISTRY as Record<string, CardRegistration>)[key] ?? null
}
