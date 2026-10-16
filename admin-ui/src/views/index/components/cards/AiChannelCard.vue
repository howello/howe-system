<template>
  <HomeCard
    title="AI 渠道健康"
    icon-text="渠"
    layer-tag="模块层 · ai"
    link-text="渠道管理"
    link-to="/ai/channel"
    link-icon-name="channel"
    link-title="进入渠道管理"
  >
    <CardState :state="state" :message="errorMessage" @retry="reload">
      <div v-if="channels.length === 0" class="ch-empty">暂无渠道</div>
      <div v-else class="ch-list">
        <div v-for="ch in channels" :key="ch.name" class="ch-row">
          <span class="ch-name">{{ ch.name }}</span>
          <span class="ch-badge" :class="healthClass(ch.health)">{{ healthText(ch.health) }}</span>
        </div>
      </div>
      <div class="ch-meta">
        <div class="ch-meta-row">
          <span class="ch-meta-k">最近检测</span>
          <span class="ch-meta-v">{{ data?.lastCheckTime || '—' }}</span>
        </div>
        <div class="ch-meta-row">
          <span class="ch-meta-k">停用渠道</span>
          <span class="ch-meta-v">{{ data?.disabledCount ?? 0 }} 个</span>
        </div>
      </div>
      <div>
        <span v-if="downCount > 0" class="badge badge-bad">✕ {{ downCount }} 个渠道异常</span>
        <span v-else class="badge badge-ok">✓ 全部正常</span>
      </div>
    </CardState>
  </HomeCard>
</template>

<script setup lang="ts" name="AiChannelCard">
// AI 渠道健康卡：各渠道健康状态、异常与停用数、最近检测时间
import HomeCard from '../HomeCard.vue'
import CardState from '../CardState.vue'
import { getAiChannelHealth } from '@/api/home/cards'
import type { AiChannelHealth, AjaxResult } from '@/types'
import { useCardData } from '../../use-card-data'

const data = ref<AiChannelHealth | null>(null)

const channels = computed(() => data.value?.channels ?? [])
const downCount = computed(() => data.value?.downCount ?? 0)

/** 健康状态文案：带图标，不靠颜色单独表意 */
function healthText(health: string): string {
  if (health === 'UP') return '✓ UP'
  if (health === 'DOWN') return '✕ DOWN'
  return '– 未检测'
}

function healthClass(health: string): string {
  if (health === 'UP') return 'is-ok'
  if (health === 'DOWN') return 'is-bad'
  return 'is-idle'
}

async function load(): Promise<void> {
    const res: AjaxResult<AiChannelHealth> = await getAiChannelHealth()
    data.value = res.data ?? null
}

const { state, errorMessage, reload } = useCardData(load)

defineExpose({ reload })
</script>

<style scoped lang="scss">
.ch-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.ch-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  border-radius: 8px;
  background: var(--home-tile-bg);
  border: 1px solid transparent;
  font-size: 13px;
  transition: all 0.16s;

  &:hover {
    border-color: var(--home-line);
    background: var(--home-card-bg);
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  }
}

.ch-name {
  color: var(--home-text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ch-badge {
  margin-left: auto;
  flex: none;
  font-size: 12px;
  padding: 3px 10px;
  border-radius: 999px;
  white-space: nowrap;
  border: 1px solid transparent;

  &.is-ok {
    background: var(--home-ok-bg);
    color: var(--home-ok-text);
    border-color: var(--home-ok-border);
  }

  &.is-bad {
    background: var(--home-bad-bg);
    color: var(--home-bad-text);
    border-color: var(--home-bad-border);
  }

  &.is-idle {
    background: var(--home-tile-bg);
    color: var(--home-text-muted);
    border-color: var(--home-line);
  }
}

.ch-meta {
  display: flex;
  flex-direction: column;
  gap: 6px;
  border-top: 1px solid var(--home-line-light);
  padding-top: 10px;
}

.ch-meta-row {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 13px;
}

.ch-meta-k {
  color: var(--home-text-secondary);
}

.ch-meta-v {
  margin-left: auto;
  color: var(--home-text-primary);
  font-weight: 500;
  font-variant-numeric: tabular-nums;
}

.ch-empty {
  padding: 20px 0;
  text-align: center;
  font-size: 13px;
  color: var(--home-text-muted);
}

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

  &-bad {
    background: var(--home-bad-bg);
    color: var(--home-bad-text);
    border-color: var(--home-bad-border);
  }
}
</style>
