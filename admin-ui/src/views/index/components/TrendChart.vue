<template>
  <div class="trend-chart">
    <div v-if="props.trend.length === 0" class="trend-chart__empty">暂无趋势数据</div>
    <!-- 图表容器始终保留在 DOM 中（v-show 隐藏），保证 useEchart 在异步加载场景也能完成 init -->
    <div v-show="props.trend.length > 0" ref="chartEl" class="trend-chart__canvas" />
  </div>
</template>

<script setup lang="ts" name="TrendChart">
import type { EChartsOption } from 'echarts'
import type { HomeTrendPoint } from '@/types'
import { useEchart } from '@/utils/echarts'

const props = defineProps<{ trend: HomeTrendPoint[] }>()

const chartEl = ref<HTMLElement | null>(null)

function buildOption(val: HomeTrendPoint[]): EChartsOption {
  return {
    legend: { data: ['文章', '草稿', '说说'] },
    tooltip: { trigger: 'axis' },
    grid: { left: 10, right: 10, top: 30, bottom: 20, containLabel: true },
    xAxis: { type: 'category', boundaryGap: false, data: val.map((p) => p.date) },
    yAxis: { type: 'value' },
    series: [
      {
        name: '文章',
        type: 'line',
        smooth: true,
        data: val.map((p) => p.articles),
        itemStyle: { color: '#409EFF' },
      },
      {
        name: '草稿',
        type: 'line',
        smooth: true,
        data: val.map((p) => p.drafts),
        itemStyle: { color: '#67C23A' },
      },
      {
        name: '说说',
        type: 'line',
        smooth: true,
        data: val.map((p) => p.talks),
        itemStyle: { color: '#E6A23C' },
      },
    ],
  }
}

const { render } = useEchart(() => chartEl.value, buildOption(props.trend))

// val 显式标注类型：global.d.ts 的 `declare module 'vue'` 屏蔽了 vue 官方类型，
// watch 退化为 any，回调参数拿不到上下文类型（noImplicitAny 会报 TS7006）
watch(() => props.trend, (val: HomeTrendPoint[]) => render(buildOption(val)), { deep: true })
</script>

<style scoped lang="scss">
.trend-chart {
  width: 100%;
}
.trend-chart__canvas {
  width: 100%;
  height: 320px;
}
.trend-chart__empty {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 320px;
  color: #909399;
  font-size: 14px;
}
</style>