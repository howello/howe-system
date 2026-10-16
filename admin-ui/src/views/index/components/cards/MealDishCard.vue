<template>
  <HomeCard
    title="菜品概况"
    icon-text="菜"
    icon-class="ci-aqua"
    layer-tag="模块层 · meal"
    link-text="菜品管理"
    link-to="/meal/dish"
    link-icon-name="dish"
    link-title="进入菜品管理"
  >
    <CardState :state="state" :message="errorMessage" @retry="reload">
      <StatTiles :items="tiles" accent-color="var(--home-accent-aqua)" />
      <div class="kv-list">
        <div class="kv">
          <span class="kv-k">公共菜谱</span>
          <span class="kv-v">{{ data?.publicCount ?? 0 }} 道</span>
        </div>
        <div class="kv">
          <span class="kv-k">本家庭菜谱</span>
          <span class="kv-v">{{ data?.familyCount ?? 0 }} 道</span>
        </div>
      </div>
    </CardState>
  </HomeCard>
</template>

<script setup lang="ts" name="MealDishCard">
// 菜品概况卡：上架/下架菜品数、分类数、公共与家庭菜谱数
import HomeCard from '../HomeCard.vue'
import CardState from '../CardState.vue'
import StatTiles from '../StatTiles.vue'
import type { StatItem } from '../StatTiles.vue'
import { getMealDishOverview } from '@/api/home/cards'
import type { AjaxResult, MealDishOverview } from '@/types'
import { useCardData } from '../../use-card-data'

const data = ref<MealDishOverview | null>(null)

const tiles = computed<StatItem[]>(() => [
  { label: '上架菜品', value: data.value?.onShelfCount ?? 0 },
  { label: '已下架', value: data.value?.offShelfCount ?? 0 },
  { label: '分类', value: data.value?.categoryCount ?? 0 }
])

async function load(): Promise<void> {
    const res: AjaxResult<MealDishOverview> = await getMealDishOverview()
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
</style>
