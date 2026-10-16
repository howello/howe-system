import * as echarts from 'echarts'
import type { EChartsOption } from 'echarts'

/**
 * echarts 实例封装：init + setOption(notMerge) + ResizeObserver 自适应 + 卸载释放
 *
 * **惰性初始化**：容器可能因 `v-if` / 加载态在挂载时还不存在（例如卡片先显示
 * 加载态、数据到达后才渲染图表容器），因此 render 时会按需 init —— 容器一旦
 * 出现就自动补上初始化，无需调用方额外处理。
 *
 * @param getEl        取容器 DOM 的回调（组件挂载后才能拿到真实节点）
 * @param initialOption 初始 option
 * @returns { render } watch 到数据变化时调用 render(newOption) 全量重绘
 */
export function useEchart(getEl: () => HTMLElement | null, initialOption: EChartsOption) {
  let chart: echarts.ECharts | null = null
  let observer: ResizeObserver | null = null
  let lastOption: EChartsOption = initialOption

  /** 容器就绪时确保实例已创建（惰性初始化） */
  function ensureChart(): echarts.ECharts | null {
    if (chart) {
      return chart
    }
    const el = getEl()
    if (!el) {
      return null
    }
    chart = echarts.init(el)
    observer = new ResizeObserver(() => chart?.resize())
    observer.observe(el)
    return chart
  }

  function render(option: EChartsOption) {
    lastOption = option
    const instance = ensureChart()
    instance?.setOption(option, { notMerge: true })
  }

  onMounted(() => {
    // 挂载时容器可能尚未渲染（加载态），此时仅记录 option，等 render 或容器出现后初始化
    const instance = ensureChart()
    instance?.setOption(lastOption, { notMerge: true })
  })

  onBeforeUnmount(() => {
    observer?.disconnect()
    observer = null
    chart?.dispose()
    chart = null
  })

  return { render }
}
