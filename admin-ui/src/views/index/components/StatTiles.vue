<template>
  <div class="stat-tiles" :style="{ '--tile-accent': accentColor }">
    <div
      v-for="(item, idx) in items"
      :key="item.label + idx"
      class="stat-tile"
      :class="{ 'is-hot': item.hot, 'is-clickable': !!item.path }"
      :role="item.path ? 'link' : undefined"
      :tabindex="item.path ? 0 : undefined"
      :title="item.path ? `进入${item.label}` : undefined"
      @click="onTileClick(item)"
      @keydown.enter="onTileClick(item)"
    >
      <div class="stat-tile__num">
        <span class="stat-tile__cnt">{{ display(item, idx) }}</span>
        <span v-if="item.suffix" class="stat-tile__unit">{{ item.suffix }}</span>
        <span v-if="item.delta" class="stat-tile__delta">{{ item.delta }}</span>
      </div>
      <div class="stat-tile__label" :title="item.label">{{ item.label }}</div>
    </div>
  </div>
</template>

<script setup lang="ts" name="StatTiles">
// 首页统计数字卡：等分铺满整行，数字从 0 滚动到目标值
// 遵循系统「减弱动效」设置时直接落终值
// 带 path 的数字块可点击直接跳转（足迹卡等用它替代额外的按钮行）
import { useRouter } from 'vue-router'

const router = useRouter()

/** 点击数字块跳转（仅带 path 的项响应） */
function onTileClick(item: StatItem): void {
  if (item.path) router.push(item.path)
}

/** 单个数字项 */
export interface StatItem {
  /** 标签 */
  label: string
  /** 目标值；null 表示无数据（显示占位或 text） */
  value: number | null
  /** 小数位（默认 0） */
  decimals?: number
  /** 单位后缀（K / MB / %） */
  suffix?: string
  /** 附加说明（如「今日 +3」） */
  delta?: string
  /** 是否用主色高亮（待处理这类需要一眼看到的量） */
  hot?: boolean
  /** 非数字文本值（如版本号）；存在时直接展示，不参与滚动动画 */
  text?: string
  /** 跳转路径；存在时整个数字块可点击跳转 */
  path?: string
}

const props = withDefaults(
  defineProps<{
    /** 数字项列表 */
    items: StatItem[]
    /** 主色（跟随卡片主色，默认主色蓝） */
    accentColor?: string
  }>(),
  { accentColor: 'var(--home-accent)' }
)

/** 滚动中的当前值：按索引记录，避免项数变化时错位 */
const current = ref<number[]>([])

const reduceMotion =
  typeof window !== 'undefined' &&
  typeof window.matchMedia === 'function' &&
  window.matchMedia('(prefers-reduced-motion: reduce)').matches

function easeOutCubic(t: number): number {
  return 1 - Math.pow(1 - t, 3)
}

/** 格式化：小数位 + 千分位 */
function format(value: number, decimals: number): string {
  const fixed = value.toFixed(decimals)
  const parts = fixed.split('.')
  parts[0] = parts[0].replace(/\B(?=(\d{3})+(?!\d))/g, ',')
  return parts.join('.')
}

/** 渲染用值：文本值直接展示；未滚动到位的显示中间值；null 显示占位 */
function display(item: StatItem, idx: number): string {
  if (item.text !== undefined) return item.text
  if (item.value === null) return '—'
  const decimals = item.decimals ?? 0
  const val = current.value[idx]
  if (val === undefined) return format(item.value, decimals)
  return format(val, decimals)
}

let rafId: number | null = null

function play(): void {
  if (rafId !== null) cancelAnimationFrame(rafId)
  const targets = props.items.map((it: StatItem) => it.value)
  if (reduceMotion) {
    current.value = targets.map((v: number | null) => (v === null ? 0 : v))
    return
  }
  current.value = targets.map(() => 0 as number)
  const duration = 900
  let start: number | null = null
  const step = (ts: number): void => {
    if (start === null) start = ts
    const p = Math.min((ts - start) / duration, 1)
    const eased = easeOutCubic(p)
    current.value = targets.map((v: number | null) => (v === null ? 0 : v * eased))
    if (p < 1) {
      rafId = requestAnimationFrame(step)
    } else {
      current.value = targets.map((v: number | null) => (v === null ? 0 : v))
      rafId = null
    }
  }
  rafId = requestAnimationFrame(step)
}

// 数据变化时重播（首次拿到数据、手动刷新都会触发）
watch(
  () => props.items.map((it: StatItem) => it.value).join(','),
  () => play(),
  { immediate: true }
)

onBeforeUnmount(() => {
  if (rafId !== null) cancelAnimationFrame(rafId)
})
</script>

<style scoped lang="scss">
.stat-tiles {
  display: grid;
  grid-auto-flow: column;
  grid-auto-columns: minmax(0, 1fr);
  gap: 10px;
  align-items: stretch;
}

.stat-tile {
  position: relative;
  overflow: hidden;
  min-width: 0;
  padding: 13px 14px 12px;
  border-radius: 10px;
  background: var(--home-tile-bg);
  border: 1px solid var(--home-line);
  display: flex;
  flex-direction: column;
  gap: 5px;
  transition: box-shadow 0.18s;

  &::before {
    content: '';
    position: absolute;
    left: 0;
    top: 0;
    bottom: 0;
    width: 3px;
    background: var(--tile-accent);
  }

  // 右上角淡色底纹：让数字块有层次，不喧宾夺主
  &::after {
    content: '';
    position: absolute;
    right: -18px;
    top: -18px;
    width: 56px;
    height: 56px;
    border-radius: 50%;
    background: var(--tile-accent);
    opacity: 0.06;
    pointer-events: none;
  }

  &:hover {
    box-shadow: 0 2px 10px rgba(0, 0, 0, 0.1);
  }

  // 可点击的数字块：hover 时边框转主色 + 指针变化
  &.is-clickable {
    cursor: pointer;

    &:hover {
      border-color: var(--tile-accent);
    }

    &:focus-visible {
      outline: 2px solid var(--tile-accent);
      outline-offset: 2px;
    }
  }

  .stat-tile__num {
    display: flex;
    align-items: baseline;
    flex-wrap: wrap;
    gap: 0 1px;
    font-size: 28px;
    font-weight: 650;
    line-height: 1.1;
    letter-spacing: -0.02em;
    color: var(--home-text-primary);
    // 大数字用比例数字（非 tabular），避免 121 这类数字显得松散
    font-feature-settings: 'tnum' 0;
  }

  &.is-hot &__num {
    color: var(--home-accent-orange);
  }

  &__unit {
    font-size: 15px;
    font-weight: 600;
    color: var(--home-text-secondary);
    margin-left: 1px;
    letter-spacing: 0;
  }

  &__delta {
    font-size: 12px;
    font-weight: 500;
    color: var(--home-ok-text);
    margin-left: 7px;
    letter-spacing: 0;
    white-space: nowrap;
  }

  &__label {
    font-size: 12px;
    color: var(--home-text-muted);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
}

// 窄屏：数字卡改两列，避免数字被压得过窄
@media (max-width: 768px) {
  .stat-tiles {
    grid-auto-flow: row;
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
