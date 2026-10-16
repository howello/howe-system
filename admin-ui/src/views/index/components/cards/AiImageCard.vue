<template>
  <HomeCard
    title="AI 图片资产"
    icon-text="图"
    layer-tag="模块层 · ai"
    link-text="查看图片"
    link-to="/ai/calllog"
    link-icon-name="image"
    link-title="进入调用记录查看生成图"
  >
    <CardState :state="state" :message="errorMessage" @retry="reload">
      <StatTiles :items="tiles" accent-color="var(--home-accent)" />
      <div class="kv-list">
        <div class="kv">
          <span class="kv-k">最近生成</span>
          <span class="kv-v">{{ data?.latestTime || '—' }}</span>
        </div>
        <div class="kv">
          <span class="kv-k">平均单张</span>
          <span class="kv-v">{{ avgSize }}</span>
        </div>
      </div>
    </CardState>
  </HomeCard>
</template>

<script setup lang="ts" name="AiImageCard">
// AI 图片资产卡：已生成图片总数、占用空间与最近生成时间
import HomeCard from '../HomeCard.vue'
import CardState from '../CardState.vue'
import StatTiles from '../StatTiles.vue'
import type { StatItem } from '../StatTiles.vue'
import { getAiImageAssets } from '@/api/home/cards'
import type { AiImageAssetOverview, AjaxResult } from '@/types'
import { useCardData } from '../../use-card-data'

const data = ref<AiImageAssetOverview | null>(null)

/** 字节按量级换算为 MB / GB */
function formatBytes(bytes: number): { value: number; suffix: string } {
  if (bytes >= 1024 * 1024 * 1024) return { value: Math.round(bytes / (1024 * 1024 * 1024) * 10) / 10, suffix: 'GB' }
  if (bytes >= 1024 * 1024) return { value: Math.round(bytes / (1024 * 1024) * 10) / 10, suffix: 'MB' }
  if (bytes >= 1024) return { value: Math.round(bytes / 1024), suffix: 'KB' }
  return { value: bytes, suffix: 'B' }
}

const size = computed(() => formatBytes(data.value?.totalBytes ?? 0))

const tiles = computed<StatItem[]>(() => [
  { label: '已生成图片', value: data.value?.totalCount ?? 0 },
  { label: '占用空间', value: size.value.value, decimals: size.value.suffix === 'GB' || size.value.suffix === 'MB' ? 1 : 0, suffix: size.value.suffix }
])

/** 平均单张大小 */
const avgSize = computed(() => {
  const count = data.value?.totalCount ?? 0
  if (count === 0) return '—'
  const avg = formatBytes((data.value?.totalBytes ?? 0) / count)
  return `${avg.value} ${avg.suffix}`
})

async function load(): Promise<void> {
    const res: AjaxResult<AiImageAssetOverview> = await getAiImageAssets()
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
