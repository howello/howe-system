<template>
  <main class="mcp-page">
    <section class="mcp-card mcp-card--wide" aria-labelledby="operations-title">
      <McpBrand badge="我的操作" />

      <h1 id="operations-title">我的 MCP 写操作</h1>
      <p class="mcp-intro">这里只显示你本人发起的写操作状态。结果未确认的操作会标记为“待核对”，不要重复发起。</p>

      <div class="mcp-userbar">
        <div class="mcp-userbar__who">
          <span class="mcp-userbar__avatar" aria-hidden="true">{{ avatarText }}</span>
          <span>当前账号 <span class="mcp-userbar__name">{{ username || '读取中…' }}</span></span>
        </div>
        <div class="mcp-userbar__actions">
          <el-button size="small" :icon="Refresh" :disabled="busy" @click="load">刷新</el-button>
          <router-link class="mcp-back" to="/mcp/connections">返回我的连接</router-link>
        </div>
      </div>

      <el-alert v-if="error" :title="error" type="error" :closable="false" class="mcp-error" role="alert" show-icon />

      <section class="mcp-section">
        <div class="mcp-section__head">
          <div>
            <h2 class="mcp-section__title">操作记录</h2>
            <p class="mcp-section__desc">共 {{ total }} 条，只有本人账号可见。</p>
          </div>
        </div>
        <div class="mcp-section__body">
          <el-table :data="items" v-loading="busy" empty-text="暂无写操作" row-key="operationId">
            <el-table-column label="操作" min-width="200">
              <template #default="{ row }">
                <div class="mcp-client-cell__name">{{ row.toolName || row.operationId }}</div>
                <div class="mcp-client-cell__id mcp-mono">{{ row.operationId }}</div>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="150">
              <template #default="{ row }">
                <span class="mcp-state" :class="stateClass(row.state)">{{ stateLabel(row.state) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="结果" min-width="160">
              <template #default="{ row }">
                <span v-if="row.state === 'UNKNOWN'" class="mcp-unknown">结果待核对，勿重新发起</span>
                <span v-else class="mcp-muted">{{ row.resultCode || '—' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="时间" min-width="170">
              <template #default="{ row }"><span class="mcp-time">{{ formatTime(row.createdAt) }}</span></template>
            </el-table-column>
            <el-table-column label="操作" width="110" align="center">
              <template #default="{ row }">
                <router-link v-if="row.state === 'PREPARED'" :to="`/mcp/confirm?operationId=${row.operationId}`">
                  去确认
                </router-link>
                <span v-else class="mcp-muted">—</span>
              </template>
            </el-table-column>
          </el-table>
        </div>
        <div v-if="total > 20" class="mcp-section__foot">
          <el-pagination
            v-model:current-page="page"
            :total="total"
            :page-size="20"
            layout="prev, pager, next"
            background
            @current-change="load"
          />
        </div>
      </section>
    </section>
  </main>
</template>

<script setup lang="ts" name="McpOperations">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { createMcpBrowserClient, mcpErrorMessage, type McpBrowserClient } from '@/api/mcp/browser'
import { McpOperationClient } from '@/api/mcp/operation'
import type { McpOperationState, McpOperationView } from '@/types'
import McpBrand from './components/McpBrand.vue'
import './style.css'

const busy = ref(false)
const error = ref('')
const username = ref('')
const items = ref<McpOperationView[]>([])
const total = ref(0)
const page = ref(1)
let browser: McpBrowserClient | undefined
/** 自动刷新定时器；终态或卸载时清理。 */
let pollTimer: number | undefined
/** 卸载标志；在途请求返回后不得再安排轮询。 */
let unmounted = false
/** 每次刷新递增，忽略旧请求返回。 */
let loadVersion = 0

const avatarText = computed(() => (username.value || '?').slice(0, 1).toUpperCase())

/** 状态中文名。 */
function stateLabel(state: McpOperationState): string {
  const map: Record<string, string> = {
    PREVIEW: '预览', PREPARED: '待确认', CONFIRMED: '已确认', QUEUED: '已排队', RUNNING: '执行中',
    SUCCEEDED: '已成功', FAILED: '失败', UNKNOWN: '结果待核对', CANCELLED: '已取消', EXPIRED: '已过期'
  }
  return map[state] ?? state
}

/** 状态样式。 */
function stateClass(state: McpOperationState): string {
  if (state === 'SUCCEEDED') return 'mcp-state--on'
  if (state === 'PREPARED' || state === 'QUEUED' || state === 'RUNNING') return 'mcp-state--pending'
  return 'mcp-state--off'
}

/** 时间格式化；空值显示占位。 */
function formatTime(value?: string | null): string {
  return value ? new Date(value).toLocaleString() : '—'
}

/** 加载本人操作；有未完成操作时自动轮询。 */
async function load(): Promise<void> {
  if (unmounted) return
  const version = ++loadVersion
  stopPoll()
  busy.value = true
  error.value = ''
  try {
    if (!browser) {
      const created = await createMcpBrowserClient()
      if (unmounted || version !== loadVersion) return
      const session = await created.client.session()
      if (unmounted || version !== loadVersion) return
      browser = created.client
      username.value = session.username
    }
    const result = await new McpOperationClient(browser).list(page.value)
    if (unmounted || version !== loadVersion) return
    items.value = result.items
    total.value = result.total
    schedulePoll()
  } catch (cause) {
    if (!unmounted && version === loadVersion) error.value = mcpErrorMessage(cause)
  } finally {
    if (!unmounted && version === loadVersion) busy.value = false
  }
}

/** 终止态集合；不在其中的操作仍可能变化。 */
const FINAL_STATES: McpOperationState[] = ['SUCCEEDED', 'FAILED', 'CANCELLED', 'EXPIRED']

/**
 * 存在未完成操作时安排下一次拉取。
 *
 * <p>仅在存在 QUEUED/RUNNING/PREPARED 时轮询，全部进入终态后自动停止，
 * 避免页面长期空转；切页或重新加载会重置定时器。</p>
 */
function schedulePoll(): void {
  stopPoll()
  if (unmounted) {
    return
  }
  const pending = items.value.some(item => !FINAL_STATES.includes(item.state))
  if (!pending) {
    return
  }
  const version = loadVersion
  pollTimer = window.setTimeout(async () => {
    if (!browser || unmounted) {
      return
    }
    try {
      const result = await new McpOperationClient(browser).list(page.value)
      // 请求在途期间可能已卸载，返回后不得写状态或再排定时器。
      if (unmounted || version !== loadVersion) {
        return
      }
      items.value = result.items
      total.value = result.total
      schedulePoll()
    } catch {
      // 轮询失败不覆盖用户已看到的数据，下次重试由手动刷新承担。
      stopPoll()
    }
  }, 2000)
}

/** 清理轮询定时器。 */
function stopPoll(): void {
  if (pollTimer !== undefined) {
    window.clearTimeout(pollTimer)
    pollTimer = undefined
  }
}

onMounted(load)

onBeforeUnmount(() => {
  unmounted = true
  loadVersion++
  stopPoll()
})
</script>
