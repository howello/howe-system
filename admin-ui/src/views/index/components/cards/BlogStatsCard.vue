<template>
  <HomeCard
    title="博客统计"
    icon-text="博"
    icon-class="ci-orange"
    layer-tag="模块层 · blog"
    link-text="文章列表"
    link-to="/blog/article"
    link-icon-name="article"
    link-title="进入文章列表"
  >
    <CardState :state="state" :message="errorMessage" @retry="reload">
      <StatTiles :items="tiles" accent-color="var(--home-accent-orange)" />
    </CardState>
  </HomeCard>
</template>

<script setup lang="ts" name="BlogStatsCard">
// 博客统计数字卡（M1）：文章/草稿/说说总数与今日新增
// 数据源复用既有 /home/stats/summary（口径与降级行为不变）
// 趋势图与分类图拆为独立卡片（M14 / M15）
import HomeCard from '../HomeCard.vue'
import CardState from '../CardState.vue'
import StatTiles from '../StatTiles.vue'
import type { StatItem } from '../StatTiles.vue'
import { getHomeStats } from '@/api/home/stats'
import { useCardData } from '../../use-card-data'
import type { AjaxResult, HomeStats } from '@/types'

const stats = ref<HomeStats | null>(null)

const blog = computed(() => stats.value?.blog ?? null)

const tiles = computed<StatItem[]>(() => {
  const b = blog.value
  const todayTotal = (b?.today.articles ?? 0) + (b?.today.drafts ?? 0) + (b?.today.talks ?? 0)
  return [
    { label: '文章', path: '/blog/article', value: b?.articleTotal ?? 0 },
    { label: '草稿', path: '/blog/draft', value: b?.draftTotal ?? 0 },
    { label: '说说', path: '/blog/talk', value: b?.talkTotal ?? 0 },
    { label: '今日新增合计', value: todayTotal, delta: `↑ 今日 +${todayTotal}` }
  ]
})

async function load(): Promise<void> {
  const res: AjaxResult<HomeStats> = await getHomeStats()
  stats.value = res.data ?? null
}

const { state, errorMessage, reload } = useCardData(load)

defineExpose({ reload })
</script>
