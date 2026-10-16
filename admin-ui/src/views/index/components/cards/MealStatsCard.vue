<template>
  <HomeCard
    title="点餐统计"
    icon-text="餐"
    icon-class="ci-aqua"
    layer-tag="模块层 · meal · 本家庭"
    link-text="订单列表"
    link-to="/meal/order"
    link-icon-name="order"
    link-title="进入订单列表"
  >
    <CardState :state="state" :message="errorMessage" @retry="reload">
      <StatTiles :items="tiles" accent-color="var(--home-accent-aqua)" />
      <HomeChart
        :option="option"
        :height="170"
        :is-empty="!hasTrend"
        empty-text="暂无订单数据"
        table-caption="近 7 天订单量"
        :table-columns="tableColumns"
        :table-rows="tableRows"
      />
    </CardState>
  </HomeCard>
</template>

<script setup lang="ts" name="MealStatsCard">
// 点餐统计卡：今日订单、状态分布、近 7 天趋势与评价均分（数据范围限本家庭）
import type { EChartsOption } from 'echarts'
import HomeCard from '../HomeCard.vue'
import CardState from '../CardState.vue'
import StatTiles from '../StatTiles.vue'
import HomeChart from '../HomeChart.vue'
import type { ChartTableColumn } from '../HomeChart.vue'
import type { StatItem } from '../StatTiles.vue'
import { getMealStats } from '@/api/home/cards'
import { CHART_COLORS, LINE_GRID, animationConfig, prefersReducedMotion } from '../../chart-config'
import type { AjaxResult, MealHomeStats, MealTrendPoint } from '@/types'
import { useCardData } from '../../use-card-data'
import { useChartTheme } from '../../use-chart-theme'

const reduceMotion = prefersReducedMotion()
const { themeVersion, refreshTheme } = useChartTheme()
const data = ref<MealHomeStats | null>(null)

const tiles = computed<StatItem[]>(() => [
  { label: '今日订单', value: data.value?.todayOrders ?? 0 },
  { label: '待接单', value: data.value?.pendingCount ?? 0, hot: (data.value?.pendingCount ?? 0) > 0 },
  { label: '制作中', value: data.value?.cookingCount ?? 0 },
  { label: '已完成', value: data.value?.doneCount ?? 0 },
  { label: '评价均分', value: data.value?.avgScore ?? 0, decimals: 1 }
])

const trend = computed(() => data.value?.trend ?? [])
const hasTrend = computed(() => trend.value.some((p: MealTrendPoint) => p.count > 0))

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
    data: trend.value.map((p: MealTrendPoint) => p.date),
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
      name: '订单数',
      type: 'line',
      smooth: true,
      symbolSize: 6,
      data: trend.value.map((p: MealTrendPoint) => p.count),
      itemStyle: { color: CHART_COLORS.blue },
      lineStyle: { width: 2 }
    }
  ]
  }
})

const tableColumns: ChartTableColumn[] = [
  { key: 'date', label: '日期' },
  { key: 'count', label: '订单数', num: true }
]

const tableRows = computed(() => trend.value.map((p: MealTrendPoint) => ({ date: p.date, count: p.count })))

async function load(): Promise<void> {
    const res: AjaxResult<MealHomeStats> = await getMealStats()
    data.value = res.data ?? null
}

const { state, errorMessage, reload } = useCardData(load)

defineExpose({ reload, refreshTheme })
</script>
