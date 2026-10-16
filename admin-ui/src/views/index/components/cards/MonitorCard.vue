<template>
  <HomeCard
    title="系统监控与安全"
    icon-text="监"
    layer-tag="角色层"
    link-text="监控"
    link-to="/monitor/online"
    link-icon-name="monitor"
    link-title="进入在线用户监控"
  >
    <CardState :state="state" :message="errorMessage" @retry="reload">
      <StatTiles :items="tiles" accent-color="var(--home-accent-orange)" />
      <div v-if="hasLogininfor" class="kv-list">
        <div class="kv">
          <span class="kv-k">最近失败登录</span>
          <span class="kv-v">{{ lastFailText }}</span>
        </div>
        <div class="kv">
          <span class="kv-k">失败原因</span>
          <span class="kv-v">{{ lastFailMsg || '—' }}</span>
        </div>
      </div>
    </CardState>
  </HomeCard>
</template>

<script setup lang="ts" name="MonitorCard">
// 系统监控与安全卡：在线用户数、今日登录失败、今日操作日志数、最近失败 IP（缓存监控已拆为独立卡片）
// 各项按权限门控：无权限的项不显示（对应计数保持 0 时也如实展示）
import HomeCard from '../HomeCard.vue'
import CardState from '../CardState.vue'
import StatTiles from '../StatTiles.vue'
import type { StatItem } from '../StatTiles.vue'
import { list as listOnline } from '@/api/monitor/online'
import { list as listLogininfor } from '@/api/monitor/logininfor'
import { list as listOperlog } from '@/api/monitor/operlog'
import useUserStore from '@/store/modules/user'
import type { SysLogininfor } from '@/types'
import { useCardData } from '../../use-card-data'

const userStore = useUserStore()

/** 与仓库既有 hasPermi 语义一致：*:*:* 通配或逐项包含 */
function hasPermi(perms: string[]): boolean {
  const permissions = userStore.permissions
  if (permissions.includes('*:*:*')) return true
  return perms.some((p) => permissions.includes(p))
}

const hasOnline = hasPermi(['monitor:online:list'])
const hasLogininfor = hasPermi(['monitor:logininfor:list'])
const hasOperlog = hasPermi(['monitor:operlog:list'])

const onlineTotal = ref(0)
const todayFailCount = ref(0)
const todayOperCount = ref(0)
const lastFailIp = ref('')
const lastFailLocation = ref('')
const lastFailMsg = ref('')

const lastFailText = computed(() => {
  if (!lastFailIp.value) return '—'
  return lastFailLocation.value ? `${lastFailIp.value}（${lastFailLocation.value}）` : lastFailIp.value
})

const tiles = computed<StatItem[]>(() => {
  const items: StatItem[] = []
  if (hasOnline) items.push({ label: '在线用户', value: onlineTotal.value })
  if (hasLogininfor) items.push({ label: '今日登录失败', value: todayFailCount.value, hot: todayFailCount.value > 0 })
  if (hasOperlog) items.push({ label: '今日操作日志', value: todayOperCount.value })
  return items
})

/** 今日 00:00:00 与 23:59:59（后端按字符串比较时间） */
function todayRange(): { beginTime: string; endTime: string } {
  const now = new Date()
  const y = now.getFullYear()
  const m = String(now.getMonth() + 1).padStart(2, '0')
  const d = String(now.getDate()).padStart(2, '0')
  return { beginTime: `${y}-${m}-${d} 00:00:00`, endTime: `${y}-${m}-${d} 23:59:59` }
}

async function load(): Promise<void> {
  const jobs: Promise<unknown>[] = []
  const range = todayRange()

  if (hasOnline) {
    jobs.push(
      listOnline({}).then((r) => {
        onlineTotal.value = r.total ?? 0
      })
    )
  }
  if (hasLogininfor) {
    jobs.push(
      listLogininfor({ status: '1', params: range, pageNum: 1, pageSize: 10 }).then((r) => {
        const rows = (r.rows ?? []) as SysLogininfor[]
        todayFailCount.value = r.total ?? rows.length
        const first = rows[0]
        if (first) {
          lastFailIp.value = first.ipaddr ?? ''
          lastFailLocation.value = first.loginLocation ?? ''
          lastFailMsg.value = first.msg ?? ''
        }
      })
    )
  }
  if (hasOperlog) {
    jobs.push(
      listOperlog({ params: range, pageNum: 1, pageSize: 1 }).then((r) => {
        todayOperCount.value = r.total ?? 0
      })
    )
  }
  await Promise.allSettled(jobs)
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
  text-align: right;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
