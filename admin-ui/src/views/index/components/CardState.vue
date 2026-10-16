<template>
  <!-- 加载中 / 失败：覆盖在内容之上；内容用 v-show 保留在 DOM 中，
       使图表容器（ECharts）在数据到达后能直接初始化，无需重建组件 -->
  <div v-show="state === 'loading'" class="card-state card-state--loading">
    <span class="card-state__spinner" />
    <span class="card-state__text">加载中…</span>
  </div>
  <div v-show="state === 'failed'" class="card-state card-state--failed">
    <span class="card-state__text">{{ message || '加载失败' }}</span>
    <button class="card-state__retry" type="button" @click="emit('retry')">重试</button>
  </div>
  <div v-show="state === 'ready'" class="card-state__content">
    <slot />
  </div>
</template>

<script setup lang="ts" name="CardState">
// 卡片加载态包装：加载中 / 失败（含重试）/ 就绪
// 每张卡片独立使用，失败只影响本卡片
//
// 用 v-show 而非 v-if：内容始终在 DOM 中，图表容器在 onMounted 时即存在，
// ECharts 可正常初始化（此前用 v-if 时容器不存在，导致图表永不渲染）。

/** 卡片加载状态 */
export type CardLoadState = 'loading' | 'ready' | 'failed'

defineProps<{
  /** 当前状态 */
  state: CardLoadState
  /** 失败原因 */
  message?: string
}>()

const emit = defineEmits<{ (e: 'retry'): void }>()
</script>

<style scoped lang="scss">
.card-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  min-height: 80px;

  &__content {
    display: flex;
    flex-direction: column;
    gap: 12px;
    min-width: 0;
  }

  &__text {
    font-size: 13px;
    color: var(--home-text-muted);
  }

  &--failed &__text {
    color: var(--home-bad-text);
  }

  &__retry {
    font-size: 12px;
    color: var(--home-accent);
    background: var(--home-card-bg);
    border: 1px solid var(--home-accent);
    border-radius: 6px;
    padding: 3px 12px;
    cursor: pointer;
    font-family: inherit;

    &:hover {
      background: var(--home-tile-bg);
    }
  }

  &__spinner {
    width: 16px;
    height: 16px;
    border: 2px solid var(--home-line);
    border-top-color: var(--home-accent);
    border-radius: 50%;
    animation: card-spin 0.8s linear infinite;
  }
}

@keyframes card-spin {
  to {
    transform: rotate(360deg);
  }
}

@media (prefers-reduced-motion: reduce) {
  .card-state__spinner {
    animation: none;
  }
}
</style>
