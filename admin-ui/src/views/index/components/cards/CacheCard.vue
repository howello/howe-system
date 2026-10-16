<template>
  <HomeCard
    title="缓存监控"
    icon-text="缓"
    icon-class="ci-yellow"
    layer-tag="角色层"
    link-text="缓存列表"
    link-to="/monitor/cache"
    link-icon-name="manage"
    link-title="进入缓存监控"
  >
    <CardState :state="state" :message="errorMessage" @retry="reload">
      <StatTiles :items="tiles" accent-color="var(--home-accent-yellow)" />
      <div class="kv-list">
        <div class="kv">
          <span class="kv-k">运行模式</span>
          <span class="kv-v">{{ cacheInfo.mode }}</span>
        </div>
        <div class="kv">
          <span class="kv-k">端口</span>
          <span class="kv-v">{{ cacheInfo.port }}</span>
        </div>
        <div class="kv">
          <span class="kv-k">客户端连接</span>
          <span class="kv-v">{{ cacheInfo.connectedClients }}</span>
        </div>
        <div class="kv">
          <span class="kv-k">内存占用</span>
          <span class="kv-v">{{ cacheInfo.usedMemory }}</span>
        </div>
      </div>
      <div>
        <span v-if="hasCommandStats" class="badge badge-ok">✓ 缓存服务正常</span>
        <span v-else class="badge badge-idle">– 暂无命令统计</span>
      </div>
    </CardState>
  </HomeCard>
</template>

<script setup lang="ts" name="CacheCard">
// 缓存监控卡（R3）：Redis 版本与键数走数字块，运行模式/端口/连接数/内存走键值行
// 仅需 monitor:cache:list 权限；接口不可用时保持占位，不影响其它卡片
import HomeCard from '../HomeCard.vue'
import CardState from '../CardState.vue'
import StatTiles from '../StatTiles.vue'
import type { StatItem } from '../StatTiles.vue'
import { getCache } from '@/api/monitor/cache'
import { useCardData } from '../../use-card-data'

/** Redis 基本信息（INFO 的常用字段） */
interface CacheInfo {
  version: string
  mode: string
  port: string
  connectedClients: string
  usedMemory: string
  dbSize: string
}

const cacheInfo = ref<CacheInfo>({
  version: '—',
  mode: '—',
  port: '—',
  connectedClients: '—',
  usedMemory: '—',
  dbSize: '—'
})

const hasCommandStats = ref(false)

/** 数字块：键数与版本（版本为文本值，不参与滚动动画） */
const tiles = computed<StatItem[]>(() => [
  { label: '缓存键数', value: Number(cacheInfo.value.dbSize) || 0 },
  { label: 'Redis 版本', value: null, text: cacheInfo.value.version }
])

async function load(): Promise<void> {
  const res = await getCache()
  const info = (res.data?.info ?? {}) as Record<string, string>
  // Redis INFO 的键名形如 redis_version / redis_mode / tcp_port 等
  cacheInfo.value = {
    version: info.redis_version || '—',
    mode: info.redis_mode || '—',
    port: info.tcp_port || '—',
    connectedClients: info.connected_clients || '—',
    usedMemory: info.used_memory_human || '—',
    dbSize: String(res.data?.dbSize ?? '—')
  }
  hasCommandStats.value = Array.isArray(res.data?.commandStats) && res.data.commandStats.length > 0
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

  &-ok {
    background: var(--home-ok-bg);
    color: var(--home-ok-text);
    border-color: var(--home-ok-border);
  }

  &-idle {
    background: var(--home-tile-bg);
    color: var(--home-text-muted);
    border-color: var(--home-line);
  }
}
</style>
