<template>
  <HomeCard
    title="App 版本"
    icon-text="版"
    icon-class="ci-aqua"
    layer-tag="模块层 · meal"
    link-text="版本列表"
    link-to="/meal/appversion"
    link-icon-name="version"
    link-title="进入 App 版本列表"
  >
    <CardState :state="state" :message="errorMessage" @retry="reload">
      <StatTiles :items="tiles" accent-color="var(--home-accent-aqua)" />
      <div class="kv-list">
        <div class="kv">
          <span class="kv-k">发布时间</span>
          <span class="kv-v">{{ data?.latestPublishTime || '—' }}</span>
        </div>
        <div class="kv">
          <span class="kv-k">强制更新</span>
          <span class="kv-v">
            <span v-if="data?.forceUpdate" class="badge badge-warn">是</span>
            <span v-else class="badge badge-idle">否</span>
          </span>
        </div>
      </div>
    </CardState>
  </HomeCard>
</template>

<script setup lang="ts" name="MealAppVersionCard">
// App 版本卡：当前已发布版本、发布时间、是否强制更新、版本记录总数
import HomeCard from '../HomeCard.vue'
import CardState from '../CardState.vue'
import StatTiles from '../StatTiles.vue'
import type { StatItem } from '../StatTiles.vue'
import { getMealAppVersionOverview } from '@/api/home/cards'
import type { AjaxResult, MealAppVersionOverview } from '@/types'
import { useCardData } from '../../use-card-data'

const data = ref<MealAppVersionOverview | null>(null)

/** 版本号不是纯数字，用 text 直接展示；记录总数走滚动动画 */
const tiles = computed<StatItem[]>(() => [
  { label: '当前已发布版本', value: null, text: data.value?.latestVersionName ?? '暂无' },
  { label: '版本记录总数', value: data.value?.totalCount ?? 0 }
])

async function load(): Promise<void> {
    const res: AjaxResult<MealAppVersionOverview> = await getMealAppVersionOverview()
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
  color: var(--home-text-primary);
  font-weight: 500;
  font-variant-numeric: tabular-nums;
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

  &-idle {
    background: var(--home-tile-bg);
    color: var(--home-text-muted);
    border-color: var(--home-line);
  }

  &-warn {
    background: var(--home-warn-bg);
    color: var(--home-warn-text);
    border-color: var(--home-warn-border);
  }
}
</style>
