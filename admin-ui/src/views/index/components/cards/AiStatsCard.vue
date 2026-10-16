<template>
  <HomeCard
    title="AI 用量"
    icon-text="AI"
    layer-tag="模块层 · ai"
    link-text="调用记录"
    link-to="/ai/calllog"
    link-icon-name="calllog"
    link-title="进入 AI 调用记录"
  >
    <CardState :state="state" :message="errorMessage" @retry="reload">
      <StatTiles :items="tiles" accent-color="var(--home-accent)" />
      <HomeChart
        :option="option"
        :height="chartHeight"
        :is-empty="scenes.length === 0"
        empty-text="今日暂无调用"
        table-caption="今日按场景调用次数"
        :table-columns="tableColumns"
        :table-rows="tableRows"
      />
    </CardState>
  </HomeCard>
</template>

<script setup lang="ts" name="AiStatsCard">
// AI 用量卡：今日调用次数、token 消耗、成功率与按场景分布
import type { EChartsOption } from 'echarts'
import HomeCard from '../HomeCard.vue'
import CardState from '../CardState.vue'
import StatTiles from '../StatTiles.vue'
import HomeChart from '../HomeChart.vue'
import type { ChartTableColumn } from '../HomeChart.vue'
import type { StatItem } from '../StatTiles.vue'
import { getAiStats } from '@/api/home/cards'
import { BAR_GRID, CHART_COLORS, animationConfig, prefersReducedMotion } from '../../chart-config'
import type { AiHomeStats, AiSceneUsage, AjaxResult } from '@/types'
import { useCardData } from '../../use-card-data'
import { useChartTheme } from '../../use-chart-theme'

const reduceMotion = prefersReducedMotion()
const { themeVersion, refreshTheme } = useChartTheme()
const data = ref<AiHomeStats | null>(null)

/** token 超过 1000 时以 K 为单位展示 */
const tokenDisplay = computed(() => {
  const tokens = data.value?.todayTokens ?? 0
  if (tokens >= 1000) return { value: Math.round(tokens / 100) / 10, suffix: 'K' }
  return { value: tokens, suffix: '' }
})

const tiles = computed<StatItem[]>(() => [
  { label: '今日调用', value: data.value?.todayCalls ?? 0 },
  { label: 'token 消耗', value: tokenDisplay.value.value, decimals: tokenDisplay.value.suffix ? 1 : 0, suffix: tokenDisplay.value.suffix },
  { label: '成功率', value: data.value?.successRate ?? 0, decimals: 1, suffix: '%' }
])

const scenes = computed(() => data.value?.sceneBreakdown ?? [])

const chartHeight = computed(() => Math.max(scenes.value.length * 32 + 16, 120))

const option = computed<EChartsOption>(() => {
  // 读取主题版本号建立依赖：主题切换时本 computed 重算，图表用新配色重绘
  void themeVersion.value
  return {
  ...animationConfig(reduceMotion),
  tooltip: { trigger: 'item' },
  grid: BAR_GRID,
  xAxis: { type: 'value', show: false },
  yAxis: {
    type: 'category',
    inverse: true,
    data: scenes.value.map((s: AiSceneUsage) => s.scene),
    axisLabel: { color: CHART_COLORS.axisText, fontSize: 12 },
    axisLine: { show: false },
    axisTick: { show: false }
  },
  series: [
    {
      name: '调用次数',
      type: 'bar',
      barWidth: 16,
      data: scenes.value.map((s: AiSceneUsage) => s.count),
      itemStyle: { color: CHART_COLORS.blue, borderRadius: [4, 4, 4, 4] },
      label: {
        show: true,
        position: 'right',
        color: CHART_COLORS.ink,
        fontSize: 12,
        fontWeight: 600,
        formatter: '{c} 次'
      }
    }
  ]
  }
})

const tableColumns: ChartTableColumn[] = [
  { key: 'scene', label: '场景' },
  { key: 'count', label: '调用次数', num: true }
]

const tableRows = computed(() => scenes.value.map((s: AiSceneUsage) => ({ scene: s.scene, count: s.count })))

async function load(): Promise<void> {
    const res: AjaxResult<AiHomeStats> = await getAiStats()
    data.value = res.data ?? null
}

const { state, errorMessage, reload } = useCardData(load)

defineExpose({ reload, refreshTheme })
</script>
