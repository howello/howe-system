<template>
  <HomeCard
    title="博客趋势"
    icon-text="趋"
    icon-class="ci-orange"
    layer-tag="模块层 · blog"
    link-text="文章列表"
    link-to="/blog/article"
    link-icon-name="article"
    link-title="进入文章列表"
  >
    <CardState :state="state" :message="errorMessage" @retry="reload">
      <div v-if="hasTrend" class="blog-legend">
        <span><i style="background: var(--home-accent)" />文章</span>
        <span><i style="background: var(--home-accent-orange)" />说说</span>
      </div>
      <HomeChart
        :option="option"
        :height="220"
        :is-empty="!hasTrend"
        empty-text="暂无趋势数据"
        table-caption="近 30 天发布量（文章 / 说说）"
        :table-columns="tableColumns"
        :table-rows="tableRows"
      />
    </CardState>
  </HomeCard>
</template>

<script setup lang="ts" name="BlogTrendCard">
// 博客趋势卡（M14）：近 30 天发布量折线图 + 数据表视图
// 数据源复用既有 /home/stats/summary
import type { EChartsOption } from 'echarts'
import HomeCard from '../HomeCard.vue'
import CardState from '../CardState.vue'
import HomeChart from '../HomeChart.vue'
import type { ChartTableColumn } from '../HomeChart.vue'
import { getHomeStats } from '@/api/home/stats'
import { CHART_COLORS, LINE_GRID, animationConfig, prefersReducedMotion } from '../../chart-config'
import { useCardData } from '../../use-card-data'
import { useChartTheme } from '../../use-chart-theme'
import type { AjaxResult, HomeStats, HomeTrendPoint } from '@/types'

const reduceMotion = prefersReducedMotion()
const { themeVersion, refreshTheme } = useChartTheme()
const stats = ref<HomeStats | null>(null)

const blog = computed(() => stats.value?.blog ?? null)
const trend = computed(() => blog.value?.trend ?? [])
const hasTrend = computed(() => trend.value.length > 0)

const option = computed<EChartsOption>(() => {
  // 读取主题版本号建立依赖：主题切换时本 computed 重算，图表用新配色重绘
  void themeVersion.value
  return {
    ...animationConfig(reduceMotion),
    tooltip: { trigger: 'axis' },
    grid: LINE_GRID,
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: trend.value.map((p: HomeTrendPoint) => p.date),
      axisLabel: { color: CHART_COLORS.axisText, fontSize: 10 },
      axisLine: { lineStyle: { color: CHART_COLORS.grid } }
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      axisLabel: { color: CHART_COLORS.axisText, fontSize: 10 },
      splitLine: { lineStyle: { color: CHART_COLORS.grid } }
    },
    series: [
      {
        name: '文章',
        type: 'line',
        smooth: true,
        symbolSize: 6,
        data: trend.value.map((p: HomeTrendPoint) => p.articles),
        itemStyle: { color: CHART_COLORS.blue },
        lineStyle: { width: 2 }
      },
      {
        name: '说说',
        type: 'line',
        smooth: true,
        symbolSize: 6,
        data: trend.value.map((p: HomeTrendPoint) => p.talks),
        itemStyle: { color: CHART_COLORS.orange },
        lineStyle: { width: 2 }
      }
    ]
  }
})

const tableColumns: ChartTableColumn[] = [
  { key: 'date', label: '日期' },
  { key: 'articles', label: '文章', num: true },
  { key: 'talks', label: '说说', num: true },
  { key: 'total', label: '合计', num: true }
]

interface TrendRow {
  date: string
  articles: number
  talks: number
  total: number
  __total?: boolean
}

const tableRows = computed<TrendRow[]>(() => {
  const rows: TrendRow[] = trend.value.map((p: HomeTrendPoint) => ({
    date: p.date,
    articles: p.articles,
    talks: p.talks,
    total: p.articles + p.talks
  }))
  if (rows.length === 0) return rows
  // 合计行：加粗强调
  rows.push({
    date: '合计',
    articles: rows.reduce((a: number, r: TrendRow) => a + r.articles, 0),
    talks: rows.reduce((a: number, r: TrendRow) => a + r.talks, 0),
    total: rows.reduce((a: number, r: TrendRow) => a + r.total, 0),
    __total: true
  })
  return rows
})

async function load(): Promise<void> {
  const res: AjaxResult<HomeStats> = await getHomeStats()
  stats.value = res.data ?? null
}

const { state, errorMessage, reload } = useCardData(load)

defineExpose({ reload, refreshTheme })
</script>

<style scoped lang="scss">
.blog-legend {
  display: flex;
  gap: 14px;
  font-size: 12px;
  color: var(--home-text-secondary);

  span {
    display: inline-flex;
    align-items: center;
    gap: 6px;
  }

  i {
    width: 10px;
    height: 10px;
    border-radius: 3px;
    display: inline-block;
  }
}
</style>
