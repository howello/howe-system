<template>
  <div class="category-pie">
    <div v-if="props.categoryCounts.length === 0" class="category-pie__empty">暂无分类数据</div>
    <div v-show="props.categoryCounts.length > 0" ref="chartEl" class="category-pie__chart" />
  </div>
</template>

<script setup lang="ts" name="CategoryPie">
import type { EChartsOption } from 'echarts'
import type { HomeCategoryCount } from '@/types'
import { useEchart } from '@/utils/echarts'

const props = defineProps<{ categoryCounts: HomeCategoryCount[] }>()

const chartEl = ref<HTMLElement>()

function buildOption(list: HomeCategoryCount[]): EChartsOption {
  return {
    tooltip: {
      trigger: 'item',
      formatter: '{b}: {c}',
    },
    legend: {
      type: 'scroll',
      bottom: 0,
      icon: 'circle',
      itemWidth: 10,
      itemHeight: 10,
    },
    series: [
      {
        name: '分类分布',
        type: 'pie',
        radius: ['40%', '68%'],
        center: ['50%', '42%'],
        avoidLabelOverlap: true,
        itemStyle: {
          borderRadius: 4,
          borderColor: '#fff',
          borderWidth: 1,
        },
        label: {
          show: true,
          formatter: '{b}: {c}',
        },
        emphasis: {
          label: {
            show: true,
            fontWeight: 'bold',
          },
        },
        data: list.map((c) => ({ name: c.name, value: c.count })),
      },
    ],
  }
}

const { render } = useEchart(() => chartEl.value ?? null, buildOption(props.categoryCounts))

watch(
  () => props.categoryCounts,
  (val: HomeCategoryCount[]) => render(buildOption(val)),
  { deep: true }
)
</script>

<style scoped lang="scss">
.category-pie {
  position: relative;
  height: 320px;

  &__chart {
    width: 100%;
    height: 100%;
  }

  &__empty {
    position: absolute;
    inset: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    color: #909399;
    font-size: 14px;
  }
}
</style>
