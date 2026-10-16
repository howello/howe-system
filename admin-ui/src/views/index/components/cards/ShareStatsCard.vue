<template>
  <HomeCard
    title="分享统计"
    icon-text="享"
    icon-class="ci-yellow"
    layer-tag="模块层 · share"
    link-text="链接列表"
    link-to="/share/link"
    link-icon-name="share"
    link-title="进入分享链接列表"
  >
    <CardState :state="state" :message="errorMessage" @retry="reload">
      <StatTiles :items="tiles" accent-color="var(--home-accent-yellow)" />
      <div class="kv-list">
        <div class="kv">
          <span class="kv-k">最近创建</span>
          <span class="kv-v">{{ recent?.createTime || '—' }}</span>
        </div>
        <div class="kv">
          <span class="kv-k">最近创建文件</span>
          <span class="kv-v kv-v--file" :title="recent?.fileName">{{ recent?.fileName || '—' }}</span>
        </div>
      </div>
    </CardState>
  </HomeCard>
</template>

<script setup lang="ts" name="ShareStatsCard">
// 分享统计卡：链接总数、今日访问与下载次数、最近创建的链接
import HomeCard from '../HomeCard.vue'
import CardState from '../CardState.vue'
import StatTiles from '../StatTiles.vue'
import type { StatItem } from '../StatTiles.vue'
import { getShareStats } from '@/api/home/cards'
import type { AjaxResult, ShareHomeStats } from '@/types'
import { useCardData } from '../../use-card-data'

const data = ref<ShareHomeStats | null>(null)

const recent = computed(() => data.value?.recentLinks?.[0] ?? null)

const tiles = computed<StatItem[]>(() => [
  { label: '链接总数', value: data.value?.linkTotal ?? 0 },
  { label: '今日访问', value: data.value?.todayViews ?? 0 },
  { label: '今日下载', value: data.value?.todayDownloads ?? 0 }
])

async function load(): Promise<void> {
    const res: AjaxResult<ShareHomeStats> = await getShareStats()
    data.value = res.data ?? null
}

const { state, errorMessage, reload } = useCardData(load)

defineExpose({ reload })
</script>

<style scoped lang="scss">
.kv-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 13px;
}

.kv {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  border-radius: 8px;
  background: var(--home-tile-bg);
  border: 1px solid transparent;
  transition: all 0.16s;

  &:hover {
    border-color: var(--home-line);
    background: var(--home-card-bg);
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  }
}

.kv-k {
  color: var(--home-text-secondary);
  flex: none;
}

.kv-v {
  margin-left: auto;
  color: var(--home-text-primary);
  font-weight: 500;
  font-variant-numeric: tabular-nums;

  &--file {
    max-width: 60%;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}
</style>
