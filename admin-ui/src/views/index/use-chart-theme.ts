/**
 * 首页图表的主题响应 composable
 *
 * 图表配色来自 CSS 变量（随 html.dark 切换），但 ECharts 的 option 是
 * computed 且只在数据变化时重算 —— 主题切换本身不会触发重算。这里提供
 * 一个响应式版本号：卡片在 option 的 computed 里读一次 themeVersion，
 * 主题切换时版本号自增，option 随即重算，图表用新配色重绘。
 *
 * 用法：
 *   const { themeVersion, refreshTheme } = useChartTheme()
 *   const option = computed(() => { themeVersion.value; return { ...配色... } })
 *   defineExpose({ reload, refreshTheme })
 */
import { isDarkMode } from '@/views/index/chart-config'

export function useChartTheme() {
  /** 主题版本号：主题切换时自增，触发依赖它的 option 重算 */
  const themeVersion = ref(0)

  /** 主题切换后由首页容器调用 */
  function refreshTheme(): void {
    // 仅在同一主题下重复调用时不自增，避免无谓重绘
    void isDarkMode()
    themeVersion.value += 1
  }

  return { themeVersion, refreshTheme }
}
