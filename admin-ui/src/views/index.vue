<template>
  <div class="app-container home">
    <!-- 骨架：清单未到达时占位，避免布局跳动 -->
    <div v-if="loading" class="home-grid">
      <div v-for="i in 4" :key="i" class="home-skeleton" :class="{ 'is-wide': i === 1 }" />
    </div>

    <div v-else class="home-grid">
      <div
        v-for="card in visibleCards"
        :key="card.key"
        class="home-cell"
        :style="{ gridColumn: `span ${card.span}`, gridRow: card.rowSpan ? `span ${card.rowSpan}` : undefined }"
      >
        <component :is="card.component" :ref="(el: unknown) => registerRef(card.key, el)" @refresh="reloadAll" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts" name="Index">
// 首页工作台：按后端下发的卡片清单渲染卡片
// - 清单只决定「哪些卡片可见、按什么顺序」，UI 实现（组件与占宽）在 card-registry
// - 未出现在清单中的卡片不渲染、不请求数据（真正的按需加载）
// - 手动刷新：点击欢迎卡的刷新按钮；聚焦刷新：从其它页面切回时自动刷新
import { getHomeCards } from '@/api/home/cards'
import { resolveCard } from '@/views/index/card-registry'
import type { AjaxResult, HomeCards } from '@/types'

/** 渲染中的卡片（已过滤掉前端未注册的标识） */
interface RenderedCard {
  key: string
  component: unknown
  span: number
  /** 纵向跨行数；用于「左侧大卡跨两行」等布局 */
  rowSpan?: number
}

/** 卡片实例引用（各卡片通过 defineExpose 暴露 reload / refreshTheme） */
interface CardInstance {
  reload?: () => void
  /** 主题切换后重绘图表（卡片自行实现；无图表的卡片可不暴露） */
  refreshTheme?: () => void
}

const loading = ref(true)
const cards = ref<string[]>([])

/** 卡片实例引用：用于聚焦刷新时调用各卡片的 reload */
const cardRefs = new Map<string, CardInstance>()

function registerRef(key: string, el: unknown): void {
  if (el && typeof el === 'object') {
    cardRefs.set(key, el as CardInstance)
  }
}

const visibleCards = computed<RenderedCard[]>(() =>
  cards.value
    .map((key: string) => {
      const reg = resolveCard(key)
      // 后端先于前端上线新卡片时安全跳过，不报错、不白屏
      return reg ? { key, component: reg.component, span: reg.span, rowSpan: reg.rowSpan } : null
    })
    .filter((c: RenderedCard | null): c is RenderedCard => c !== null)
)

async function loadCards(): Promise<void> {
  try {
    const res: AjaxResult<HomeCards> = await getHomeCards()
    cards.value = res.data?.cards ?? []
  } catch {
    // 清单失败时保持空态，不阻塞页面
    cards.value = []
  } finally {
    loading.value = false
  }
}

/** 手动刷新：重新拉清单并让各卡片重新取数 */
function reloadAll(): void {
  loadCards().then(() => {
    // 等新卡片渲染完成后再触发各自 reload
    nextTick(refreshCards)
  })
}

/** 聚焦刷新：切回首页时自动刷新
 *  首页是 affix 页签且 AppMain 用 keep-alive 缓存，因此监听 onActivated；
 *  再补一条路由 watch 兜底（首页未被缓存时不走 onActivated） */
onActivated(() => {
  refreshCards()
})

const route = useRoute()
watch(
  () => route.path,
  (path: string) => {
    if (path === '/index') refreshCards()
  }
)

/** 刷新已渲染卡片的数据（不重新拉清单） */
function refreshCards(): void {
  cardRefs.forEach((ref) => ref.reload?.())
}

/** 窗口重新可见时刷新（切到其它浏览器标签再回来） */
function onVisibilityChange(): void {
  if (document.visibilityState === 'visible') {
    refreshCards()
  }
}

onMounted(() => {
  loadCards()
  document.addEventListener('visibilitychange', onVisibilityChange)
})

onBeforeUnmount(() => {
  document.removeEventListener('visibilitychange', onVisibilityChange)
  themeObserver?.disconnect()
  themeObserver = null
  cardRefs.clear()
})

/** 主题切换：监听 <html> 的 class（useDark 切换 dark 类），通知各卡片重绘图表 */
let themeObserver: MutationObserver | null = null

onMounted(() => {
  themeObserver = new MutationObserver(() => {
    nextTick(() => {
      cardRefs.forEach((ref) => ref.refreshTheme?.())
    })
  })
  themeObserver.observe(document.documentElement, { attributes: true, attributeFilter: ['class'] })
})
</script>

<style scoped lang="scss">
.home {
  // .app-container 已提供 padding: 20px，这里补背景与最小高度
  min-height: calc(100vh - 84px);
  background: var(--home-page-bg);
}

.home-grid {
  display: grid;
  grid-template-columns: repeat(12, minmax(0, 1fr));
  gap: 16px;
  // 行内卡片等高：网格行高取该行最高卡片，同行单元格拉伸对齐（内容少者底部留白）
  align-items: stretch;
}

.home-cell {
  min-width: 0;
  // 单元格必须是 flex 容器，卡片才能撑满其高度
  display: flex;
  flex-direction: column;
}

// 卡片根元素撑满单元格高度（内容靠上，多余空间留白在底部）
.home-cell > :deep(.home-card) {
  flex: 1;
  height: 100%;
}

.home-skeleton {
  grid-column: span 6;
  height: 180px;
  border-radius: 10px;
  background: var(--home-card-bg);
  border: 1px solid var(--home-card-border);
  animation: home-pulse 1.4s ease-in-out infinite;

  &.is-wide {
    grid-column: span 12;
    height: 96px;
  }
}

@keyframes home-pulse {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0.55;
  }
}

// 中屏：宽卡占整行，窄卡两两并排（保持配对不错位）
@media (max-width: 1180px) {
  .home-cell {
    grid-column: span 6 !important;
  }
}

// 窄屏：全部整行堆叠
@media (max-width: 768px) {
  .home-cell {
    grid-column: span 12 !important;
  }
}
</style>
