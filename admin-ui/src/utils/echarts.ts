import * as echarts from 'echarts'
import type { EChartsOption } from 'echarts'

/**
 * echarts 实例封装：init + setOption(notMerge) + ResizeObserver 自适应 + 卸载释放
 *
 * @param getEl        取容器 DOM 的回调（组件挂载后才能拿到真实节点）
 * @param initialOption 初始 option
 * @returns { render } watch 到数据变化时调用 render(newOption) 全量重绘
 */
export function useEchart(getEl: () => HTMLElement | null, initialOption: EChartsOption) {
  let chart: echarts.ECharts | null = null
  let observer: ResizeObserver | null = null

  function render(option: EChartsOption) {
    chart?.setOption(option, { notMerge: true })
  }

  onMounted(() => {
    const el = getEl()
    if (!el) return
    chart = echarts.init(el)
    render(initialOption)
    observer = new ResizeObserver(() => chart?.resize())
    observer.observe(el)
  })

  onBeforeUnmount(() => {
    observer?.disconnect()
    observer = null
    chart?.dispose()
    chart = null
  })

  return { render }
}