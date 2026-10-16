<template>
  <HomeCard
    title="博客分类"
    icon-text="类"
    icon-class="ci-orange"
    layer-tag="模块层 · blog"
    link-text="文章列表"
    link-to="/blog/article"
    link-icon-name="article"
    link-title="进入文章列表"
  >
    <CardState :state="state" :message="errorMessage" @retry="reload">
      <HomeChart
        :option="option"
        :height="240"
        :is-empty="categories.length === 0"
        empty-text="暂无分类数据"
        table-caption="文章分类分布"
        :table-columns="tableColumns"
        :table-rows="tableRows"
      />
    </CardState>
  </HomeCard>
</template>

<script setup lang="ts" name="BlogCategoryCard">
// 博客分类卡（M15）：文章分类分布环形图 + 数据表视图
// 数据源复用既有 /home/stats/summary 的 categoryCounts
import type { EChartsOption } from 'echarts'
import HomeCard from '../HomeCard.vue'
import CardState from '../CardState.vue'
import HomeChart from '../HomeChart.vue'
import type { ChartTableColumn } from '../HomeChart.vue'
import { getHomeStats } from '@/api/home/stats'
import { CHART_COLORS, animationConfig, prefersReducedMotion } from '../../chart-config'
import { useCardData } from '../../use-card-data'
import { useChartTheme } from '../../use-chart-theme'
import type { AjaxResult, HomeCategoryCount, HomeStats } from '@/types'

const reduceMotion = prefersReducedMotion()
const { themeVersion, refreshTheme } = useChartTheme()
const stats = ref<HomeStats | null>(null)

const blog = computed(() => stats.value?.blog ?? null)
const categories = computed(() => blog.value?.categoryCounts ?? [])

/** 分类配色：固定顺序使用图表主题色，不因数据排序改变颜色映射 */
const CATEGORY_COLORS = computed(() => [
  CHART_COLORS.blue,
  CHART_COLORS.orange,
  CHART_COLORS.aqua,
  CHART_COLORS.yellow,
  CHART_COLORS.pink,
  CHART_COLORS.violet,
  CHART_COLORS.red,
  CHART_COLORS.axisText
])

const option = computed<EChartsOption>(() => {
  // 读取主题版本号建立依赖：主题切换时重新取色
  void themeVersion.value
  return {
    ...animationConfig(reduceMotion),
    tooltip: { trigger: 'item', formatter: '{b}: {c}' },
    legend: {
      type: 'scroll',
      orient: 'vertical',
      right: 0,
      top: 'center',
      itemWidth: 10,
      itemHeight: 10,
      icon: 'circle',
      textStyle: { color: CHART_COLORS.axisText, fontSize: 12 }
    },
    series: [
      {
        name: '分类分布',
        type: 'pie',
        radius: ['46%', '70%'],
        center: ['34%', '50%'],
        avoidLabelOverlap: true,
        // 2px 表面间隙分隔扇区
        itemStyle: { borderColor: CHART_COLORS.surface, borderWidth: 2, borderRadius: 4 },
        label: { show: false },
        data: categories.value.map((c: HomeCategoryCount, i: number) => ({
          name: c.name,
          value: c.count,
          itemStyle: { color: CATEGORY_COLORS.value[i % CATEGORY_COLORS.value.length] }
        }))
      }
    ]
  }
})

const tableColumns: ChartTableColumn[] = [
  { key: 'name', label: '分类' },
  { key: 'count', label: '文章数', num: true }
]

const tableRows = computed(() =>
  categories.value.map((c: HomeCategoryCount) => ({ name: c.name, count: c.count }))
)

async function load(): Promise<void> {
  const res: AjaxResult<HomeStats> = await getHomeStats()
  stats.value = res.data ?? null
}

const { state, errorMessage, reload } = useCardData(load)

defineExpose({ reload, refreshTheme })
</script>
