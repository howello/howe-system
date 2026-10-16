/**
 * 首页图表公共配置：统一入场动画与配色
 *
 * 各卡片只构造自己的数据与坐标轴，动画/网格/文字色统一在此，保证观感一致。
 *
 * 配色从 CSS 变量读取（`home-theme.scss`），因此亮/暗模式下取到不同步进值：
 * 图表重绘时重新取值即可跟随主题，无需各卡片自己判断明暗。
 */

/** 从 CSS 变量取色；取不到时回退到给定默认值 */
function cssVar(name: string, fallback: string): string {
  if (typeof window === 'undefined' || typeof document === 'undefined') {
    return fallback
  }
  const value = getComputedStyle(document.documentElement).getPropertyValue(name).trim()
  return value || fallback
}

/** 首页图表配色（随主题取色：亮色 / 暗色两套步进值） */
export const CHART_COLORS = {
  get blue(): string {
    return cssVar('--home-accent', '#409eff')
  },
  get orange(): string {
    return cssVar('--home-accent-orange', '#eb6834')
  },
  get aqua(): string {
    return cssVar('--home-accent-aqua', '#1baf7a')
  },
  get yellow(): string {
    return cssVar('--home-accent-yellow', '#eda100')
  },
  get critical(): string {
    return cssVar('--home-status-critical', '#d03b3b')
  },
  get pink(): string {
    return cssVar('--home-accent-pink', '#e87ba4')
  },
  get violet(): string {
    return cssVar('--home-accent-violet', '#6250d6')
  },
  get red(): string {
    return cssVar('--home-accent-red', '#e34948')
  },
  get grid(): string {
    return cssVar('--home-chart-grid', '#e1e0d9')
  },
  get axisText(): string {
    return cssVar('--home-chart-axis-text', '#909399')
  },
  get ink(): string {
    return cssVar('--home-chart-ink', '#0b0b0b')
  },
  /** 卡片/图表背景（环形图扇区间隙、饼图描边用） */
  get surface(): string {
    return cssVar('--home-card-bg', '#ffffff')
  }
}

/** 入场动画配置：折线画出、条形长出、扇区扫出；减弱动效时时长置 0 */
export function animationConfig(reduceMotion: boolean) {
  return {
    animation: !reduceMotion,
    animationDuration: reduceMotion ? 0 : 900,
    animationEasing: 'cubicOut' as const,
    animationDelay: (idx: number) => (reduceMotion ? 0 : idx * 60)
  }
}

/** 是否开启「减弱动效」 */
export function prefersReducedMotion(): boolean {
  return (
    typeof window !== 'undefined' &&
    typeof window.matchMedia === 'function' &&
    window.matchMedia('(prefers-reduced-motion: reduce)').matches
  )
}

/** 当前是否处于暗色模式（跟随应用既有的 html.dark） */
export function isDarkMode(): boolean {
  if (typeof document === 'undefined') {
    return false
  }
  return document.documentElement.classList.contains('dark')
}

/** 折线图公共网格配置 */
export const LINE_GRID = { left: 10, right: 12, top: 30, bottom: 6, containLabel: true }

/** 横向条形图公共网格配置 */
export const BAR_GRID = { left: 10, right: 56, top: 6, bottom: 6, containLabel: true }
