<template>
  <HomeCard
    title="分享缓存健康"
    icon-text="缓"
    icon-class="ci-yellow"
    layer-tag="模块层 · share"
    link-text="链接列表"
    link-to="/share/link"
    link-icon-name="share"
    link-title="进入分享链接列表"
  >
    <CardState :state="state" :message="errorMessage" @retry="reload">
      <HomeChart
        :option="option"
        :height="chartHeight"
        :is-empty="total === 0"
        empty-text="暂无分享链接"
        table-caption="分享链接缓存状态分布"
        :table-columns="tableColumns"
        :table-rows="tableRows"
      />
      <div>
        <span v-if="failedCount > 0" class="badge badge-warn">⚠ {{ failedCount }} 个链接缓存失败</span>
        <span v-else class="badge badge-ok">✓ 缓存正常</span>
      </div>
    </CardState>
  </HomeCard>
</template>

<script setup lang="ts" name="ShareCacheCard">
// 分享缓存健康卡：就绪 / 待缓存 / 失败数量（环形图 + 图例）
import type { EChartsOption } from 'echarts'
import HomeCard from '../HomeCard.vue'
import CardState from '../CardState.vue'
import HomeChart from '../HomeChart.vue'
import type { ChartTableColumn } from '../HomeChart.vue'
import { getShareCacheHealth } from '@/api/home/cards'
import { CHART_COLORS, animationConfig, prefersReducedMotion } from '../../chart-config'
import type { AjaxResult, ShareCacheHealth } from '@/types'
import { useCardData } from '../../use-card-data'
import { useChartTheme } from '../../use-chart-theme'

const reduceMotion = prefersReducedMotion()
const { themeVersion, refreshTheme } = useChartTheme()
const data = ref<ShareCacheHealth | null>(null)

const readyCount = computed(() => data.value?.readyCount ?? 0)
const pendingCount = computed(() => data.value?.pendingCount ?? 0)
const failedCount = computed(() => data.value?.failedCount ?? 0)
const total = computed(() => readyCount.value + pendingCount.value + failedCount.value)

/** 环形图：窄屏下与图例纵向排布，用容器高度兜住 */
const chartHeight = 150

const option = computed<EChartsOption>(() => {
  // 读取主题版本号建立依赖：主题切换时本 computed 重算，图表用新配色重绘
  void themeVersion.value
  return {
  ...animationConfig(reduceMotion),
  tooltip: { trigger: 'item', formatter: '{b}: {c}' },
  legend: {
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
      name: '缓存状态',
      type: 'pie',
      radius: ['48%', '72%'],
      center: ['32%', '50%'],
      avoidLabelOverlap: true,
      itemStyle: {
        borderRadius: 4,
        borderColor: CHART_COLORS.surface,
        borderWidth: 2
      },
      label: {
        show: true,
        position: 'center',
        formatter: () => `{v|${total.value}}\n{l|链接总数}`,
        rich: {
          v: { fontSize: 20, fontWeight: 600, color: CHART_COLORS.ink, lineHeight: 26 },
          l: { fontSize: 11, color: CHART_COLORS.axisText }
        }
      },
      emphasis: { label: { show: true } },
      data: [
        { name: '已就绪', value: readyCount.value, itemStyle: { color: CHART_COLORS.blue } },
        { name: '待缓存', value: pendingCount.value, itemStyle: { color: CHART_COLORS.yellow } },
        { name: '缓存失败', value: failedCount.value, itemStyle: { color: CHART_COLORS.critical } }
      ]
    }
  ]
  }
})

const tableColumns: ChartTableColumn[] = [
  { key: 'status', label: '状态' },
  { key: 'count', label: '数量', num: true }
]

const tableRows = computed(() => [
  { status: '已就绪', count: readyCount.value },
  { status: '待缓存', count: pendingCount.value },
  { status: '缓存失败', count: failedCount.value }
])

async function load(): Promise<void> {
    const res: AjaxResult<ShareCacheHealth> = await getShareCacheHealth()
    data.value = res.data ?? null
}

const { state, errorMessage, reload } = useCardData(load)

defineExpose({ reload, refreshTheme })
</script>

<style scoped lang="scss">
.badge {
  font-size: 12px;
  padding: 3px 10px;
  border-radius: 999px;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  white-space: nowrap;
  border: 1px solid transparent;

  &-ok {
    background: var(--home-ok-bg);
    color: var(--home-ok-text);
    border-color: var(--home-ok-border);
  }

  &-warn {
    background: var(--home-warn-bg);
    color: var(--home-warn-text);
    border-color: var(--home-warn-border);
  }
}
</style>
