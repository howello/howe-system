<template>
  <HomeCard
    title="热门菜品"
    icon-text="热"
    icon-class="ci-aqua"
    subtitle="近 30 天被点份数 Top 5"
    link-text="菜品管理"
    link-to="/meal/dish"
    link-icon-name="dish"
    link-title="进入菜品管理"
  >
    <CardState :state="state" :message="errorMessage" @retry="reload">
      <HomeChart
        :option="option"
        :height="chartHeight"
        :is-empty="dishes.length === 0"
        empty-text="暂无点餐数据"
        table-caption="近 30 天被点份数 Top 5"
        :table-columns="tableColumns"
        :table-rows="tableRows"
      />
    </CardState>
  </HomeCard>
</template>

<script setup lang="ts" name="MealHotDishCard">
// 热门菜品卡：近 30 天已完成订单中被点份数 Top 5（横向条形图）
import type { EChartsOption } from 'echarts'
import HomeCard from '../HomeCard.vue'
import CardState from '../CardState.vue'
import HomeChart from '../HomeChart.vue'
import type { ChartTableColumn } from '../HomeChart.vue'
import { getMealHotDishes } from '@/api/home/cards'
import { BAR_GRID, CHART_COLORS, animationConfig, prefersReducedMotion } from '../../chart-config'
import type { AjaxResult, MealHotDish, MealHotDishes } from '@/types'
import { useCardData } from '../../use-card-data'
import { useChartTheme } from '../../use-chart-theme'

const reduceMotion = prefersReducedMotion()
const { themeVersion, refreshTheme } = useChartTheme()
const data = ref<MealHotDishes | null>(null)

const dishes = computed(() => data.value?.dishes ?? [])

/** 图表高度随条数增长，避免固定高度导致条形过密 */
const chartHeight = computed(() => Math.max(dishes.value.length * 32 + 16, 120))

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
    data: dishes.value.map((d: MealHotDish) => d.dishName),
    axisLabel: { color: CHART_COLORS.axisText, fontSize: 12 },
    axisLine: { show: false },
    axisTick: { show: false }
  },
  series: [
    {
      name: '份数',
      type: 'bar',
      barWidth: 16,
      data: dishes.value.map((d: MealHotDish) => d.count),
      itemStyle: { color: CHART_COLORS.blue, borderRadius: [4, 4, 4, 4] },
      label: {
        show: true,
        position: 'right',
        color: CHART_COLORS.ink,
        fontSize: 12,
        fontWeight: 600
      }
    }
  ]
  }
})

const tableColumns: ChartTableColumn[] = [
  { key: 'dishName', label: '菜品' },
  { key: 'count', label: '份数', num: true }
]

const tableRows = computed(() => dishes.value.map((d: MealHotDish) => ({ dishName: d.dishName, count: d.count })))

async function load(): Promise<void> {
    const res: AjaxResult<MealHotDishes> = await getMealHotDishes()
    data.value = res.data ?? null
}

const { state, errorMessage, reload } = useCardData(load)

defineExpose({ reload, refreshTheme })
</script>
