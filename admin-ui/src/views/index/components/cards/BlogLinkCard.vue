<template>
  <HomeCard
    title="友链与订阅同步"
    icon-text="友"
    icon-class="ci-yellow"
    layer-tag="模块层 · blog"
    link-text="友链管理"
    link-to="/blog/link"
    link-icon-name="link"
    link-title="进入友链管理"
  >
    <CardState :state="state" :message="errorMessage" @retry="reload">
      <StatTiles :items="tiles" accent-color="var(--home-accent-yellow)" />
      <div class="kv-list">
        <div class="kv">
          <span class="kv-k">最近同步</span>
          <span class="kv-v">{{ data?.lastSyncTime || '—' }}</span>
        </div>
        <div v-for="f in failures" :key="f.linkName" class="kv">
          <span class="kv-k">{{ f.linkName }}</span>
          <span class="kv-v kv-v--error">{{ f.lastError }}</span>
        </div>
      </div>
      <div>
        <span v-if="failedCount > 0" class="badge badge-warn">⚠ {{ failedCount }} 个源同步异常</span>
        <span v-else class="badge badge-ok">✓ 全部同步正常</span>
      </div>
    </CardState>
  </HomeCard>
</template>

<script setup lang="ts" name="BlogLinkCard">
// 友链与订阅同步卡：订阅源总数、同步失败数、最近同步时间与失败原因
import HomeCard from '../HomeCard.vue'
import CardState from '../CardState.vue'
import StatTiles from '../StatTiles.vue'
import type { StatItem } from '../StatTiles.vue'
import { getBlogLinkSync } from '@/api/home/cards'
import type { AjaxResult, BlogLinkSyncOverview } from '@/types'
import { useCardData } from '../../use-card-data'

const data = ref<BlogLinkSyncOverview | null>(null)

const failedCount = computed(() => data.value?.failedCount ?? 0)
const failures = computed(() => data.value?.failures ?? [])

const tiles = computed<StatItem[]>(() => [
  { label: '订阅源', value: data.value?.feedTotal ?? 0 },
  { label: '同步失败', value: failedCount.value, hot: failedCount.value > 0 }
])

async function load(): Promise<void> {
    const res: AjaxResult<BlogLinkSyncOverview> = await getBlogLinkSync()
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
}

.kv-v {
  margin-left: auto;
  text-align: right;
  color: var(--home-text-primary);
  font-weight: 500;
  font-variant-numeric: tabular-nums;

  &--error {
    color: var(--home-bad-text);
    max-width: 60%;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
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

  &-warn {
    background: var(--home-warn-bg);
    color: var(--home-warn-text);
    border-color: var(--home-warn-border);
  }
}
</style>
