<template>
  <div class="home-chart">
    <div v-if="isEmpty" class="home-chart__empty">{{ emptyText }}</div>
    <div v-show="!isEmpty" ref="chartEl" class="home-chart__canvas" :style="{ height: height + 'px' }" />
    <button v-if="tableRows.length > 0" class="home-table-toggle" type="button" :aria-expanded="tableOpen" @click="tableOpen = !tableOpen">
      <el-icon><Grid /></el-icon>
      {{ tableOpen ? '收起数据表' : '查看数据表' }}
    </button>
    <div v-if="tableOpen" class="home-table-wrap">
      <table class="home-table">
        <caption>{{ tableCaption }}</caption>
        <thead>
          <tr>
            <th v-for="h in tableHeaders" :key="h.key" :class="{ num: h.num }">{{ h.label }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(row, i) in tableRows" :key="i" :class="{ 'is-total': row.__total === true }">
            <td v-for="h in tableHeaders" :key="h.key" :class="{ num: h.num }">{{ row[h.key] }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<script setup lang="ts" name="HomeChart">
// 首页图表容器：纯渲染 ECharts + 入场动画 + 数据表视图（对比度不足时的替代读取通道）
// 动画遵循系统「减弱动效」设置：减弱时 ECharts 动画时长为 0，直接落终值
import type { EChartsOption } from 'echarts'
import { Grid } from '@element-plus/icons-vue'
import { useEchart } from '@/utils/echarts'

/** 数据表列定义 */
export interface ChartTableColumn {
  /** 列键（对应行对象的键） */
  key: string
  /** 列标题 */
  label: string
  /** 是否数值列（右对齐 + tabular 数字） */
  num?: boolean
}

const props = withDefaults(
  defineProps<{
    /** ECharts option（由各卡片自行构造，已含动画配置） */
    option: EChartsOption
    /** 图表高度（px） */
    height?: number
    /** 空态文案 */
    emptyText?: string
    /** 是否为空数据（为 true 时显示空态并隐藏图表） */
    isEmpty?: boolean
    /** 数据表标题 */
    tableCaption?: string
    /** 数据表列定义 */
    tableColumns?: ChartTableColumn[]
    /** 数据表行数据 */
    tableRows?: Record<string, string | number | boolean>[]
  }>(),
  {
    height: 170,
    emptyText: '暂无数据',
    isEmpty: false,
    tableCaption: '',
    tableColumns: () => [],
    tableRows: () => []
  }
)

const chartEl = ref<HTMLElement | null>(null)
const tableOpen = ref(false)

const tableHeaders = computed(() => props.tableColumns)

const { render } = useEchart(() => chartEl.value, props.option)

// option 变化时全量重绘（notMerge），动画随之重播
watch(
  () => props.option,
  (val: EChartsOption) => render(val),
  { deep: true }
)
</script>

<style scoped lang="scss">
.home-chart {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 0;
}

.home-chart__canvas {
  width: 100%;
}

.home-chart__empty {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 120px;
  font-size: 13px;
  color: var(--home-text-muted);
}

/* ---------- 数据表视图：与首页其它表格同一套视觉语言 ---------- */
.home-table-toggle {
  align-self: flex-start;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 12px;
  color: var(--home-text-secondary);
  padding: 4px 11px;
  border-radius: 7px;
  border: 1px solid var(--home-line);
  background: var(--home-tile-bg);
  cursor: pointer;
  transition: all 0.16s;
  font-family: inherit;
  line-height: 1.3;

  &:hover,
  &[aria-expanded='true'] {
    color: var(--home-accent);
    border-color: var(--home-accent);
    background: var(--home-card-bg);
  }
}

.home-table-wrap {
  border: 1px solid var(--home-line);
  border-radius: 10px;
  overflow: hidden;
  background: var(--home-card-bg);
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.06), 0 4px 14px rgba(0, 0, 0, 0.08);
  // 表头 sticky 需要可滚动容器
  max-height: 320px;
  overflow-y: auto;
}

.home-table {
  width: 100%;
  border-collapse: separate;
  border-spacing: 0;
  font-size: 12.5px;

  caption {
    caption-side: top;
    text-align: left;
    padding: 10px 14px 9px;
    font-size: 12px;
    font-weight: 600;
    color: var(--home-text-secondary);
    background: linear-gradient(180deg, var(--home-tile-bg), var(--home-card-bg));
    border-bottom: 1px solid var(--home-line);

    &::before {
      content: '';
      display: inline-block;
      vertical-align: -1px;
      width: 3px;
      height: 12px;
      border-radius: 2px;
      background: var(--home-accent);
      margin-right: 7px;
    }
  }

  th,
  td {
    text-align: left;
    padding: 9px 14px;
    border-bottom: 1px solid var(--home-line-light);
    // 列宽按内容自适应，避免单个文本列吃掉整行宽度
    white-space: nowrap;
  }

  thead th {
    font-weight: 600;
    font-size: 11.5px;
    color: var(--home-text-muted);
    letter-spacing: 0.03em;
    background: var(--home-tile-bg);
    border-bottom: 1px solid var(--home-line);
    // 表头吸附在容器顶部
    position: sticky;
    top: 0;
    z-index: 1;
  }

  tbody tr {
    transition: background 0.14s;

    &:nth-child(even) td {
      background: var(--home-table-zebra);
    }

    &:last-child td {
      border-bottom: none;
    }

    &:hover td {
      background: var(--home-tile-bg);
    }
  }

  .num {
    text-align: right;
    font-variant-numeric: tabular-nums;
    font-weight: 600;
    color: var(--home-text-primary);
  }

  // 合计行：加粗 + 强制底色（覆盖斑马纹）
  tbody tr.is-total td {
    border-top: 1px solid var(--home-line);
    background: var(--home-tile-bg);
    font-weight: 700;
    color: var(--home-text-primary);
  }
}
</style>
