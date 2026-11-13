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

      <el-alert
        v-if="pendingBanner"
        :title="pendingBanner"
        type="warning"
        :closable="false"
        show-icon
        class="mcp-error"
        role="status"
      >
        <router-link v-if="pendingOperationId" class="mcp-banner-link" :to="confirmPath(pendingOperationId)">点击前往确认</router-link>
      </el-alert>

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
            <el-table-column label="结果" min-width="200">
              <template #default="{ row }">
                <span v-if="row.state === 'UNKNOWN'" class="mcp-unknown">结果待核对，勿重新发起</span>
                <el-tooltip v-else-if="row.resultCode" :content="row.resultCode" placement="top">
                  <span :class="row.state === 'FAILED' ? 'mcp-failed' : 'mcp-muted'">{{ resultLabel(row) }}</span>
                </el-tooltip>
                <span v-else class="mcp-muted">—</span>
              </template>
            </el-table-column>
            <el-table-column label="时间" min-width="170">
              <template #default="{ row }"><span class="mcp-time">{{ formatTime(row.createdAt) }}</span></template>
            </el-table-column>
            <el-table-column label="操作" width="110" align="center">
              <template #default="{ row }">
                <router-link v-if="row.state === 'PREPARED'" :to="confirmPath(row.operationId)" @click="markOpened(row.operationId)">
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
    <p class="mcp-page__foot">结果未确认的操作不会被自动重试；请先核对实际业务效果。</p>
  </main>
</template>

<script setup lang="ts" name="McpOperations">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Refresh } from '@element-plus/icons-vue'
import { createMcpBrowserClient, mcpErrorMessage, type McpBrowserClient } from '@/api/mcp/browser'
import { McpOperationClient } from '@/api/mcp/operation'
import type { McpOperationState, McpOperationView } from '@/types'
import { mcpResultCodeLabel } from '@/utils/mcpResultCodes'
import McpBrand from './components/McpBrand.vue'
import './style.css'

const route = useRoute(), router = useRouter()
const busy = ref(false)
const error = ref('')
const username = ref('')
const items = ref<McpOperationView[]>([])
const total = ref(0)
const page = ref(1)
const pendingBanner = ref('')
const pendingOperationId = ref('')
let browser: McpBrowserClient | undefined
/** 自动刷新定时器；终态或卸载时清理。 */
let pollTimer: number | undefined
/** 卸载标志；在途请求返回后不得再安排轮询。 */
let unmounted = false
/** 每次刷新递增，忽略旧请求返回。 */
let loadVersion = 0

/** 本次页面进入是否已经自动尝试过打开确认页。 */
let autoOpenAttempted = false

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

/** 失败结果使用中文含义，原始码由 tooltip 保留。 */
function resultLabel(row: McpOperationView): string {
  return mcpResultCodeLabel(row.resultCode)
}

/** 时间格式化；空值显示占位。 */
function formatTime(value?: string | null): string {
  return value ? new Date(value).toLocaleString() : '—'
}

/** 固定站内确认地址，不接受任何外部 URL。 */
function confirmPath(operationId: string): string {
  return `/mcp/confirm?operationId=${encodeURIComponent(operationId)}`
}

/** 去重存储键按会话账号区分，只存导航体验，不参与服务端授权。 */
function openedKey(operationId: string): string {
  return `mcp:auto-open:${username.value}:${operationId}`
}

/** 记录已自动或手动打开过，避免刷新后重复弹窗。 */
function markOpened(operationId: string): void {
  try { sessionStorage.setItem(openedKey(operationId), '1') }
  catch { /* 存储不可用时仅本次页面内去重。 */ }
}

/** 判断本次会话是否已经尝试过打开该操作。 */
function wasOpened(operationId: string): boolean {
  try { return sessionStorage.getItem(openedKey(operationId)) === '1' }
  catch { return false }
}

/**
 * 打开固定确认页。
 *
 * <p>先打开同源空白窗口取得引用，再清除 opener 并导航，避免把
 * `window.open(..., 'noopener')` 的 null 返回值误判为拦截。失败时返回 false，
 * 由调用方展示可点击横幅。</p>
 *
 * @param path 站内确认地址
 * @returns 是否成功打开
 */
function openConfirmWindow(path: string): boolean {
  try {
    const opened = window.open('about:blank', '_blank')
    if (!opened) return false
    opened.opener = null
    opened.location.replace(path)
    return true
  } catch {
    return false
  }
}

/**
 * 进入页面时检测最新待确认操作并自动尝试打开一次。
 *
 * <p>使用独立 `PREPARED` 查询而不是当前列表页，避免被其他状态挤到后面而漏检；
 * 候选必须是合法 ID、未过期且本次会话未打开过。打开前先以只读详情复核，
 * 授权已撤销或客户端停用的旧记录会被跳过，避免自动打开到错误页。
 * 自动行为只打开详情，绝不创建确认上下文或执行写入。</p>
 */
async function autoOpenIfNeeded(): Promise<void> {
  if (autoOpenAttempted || !browser || route.query.autoOpen === '0') return
  autoOpenAttempted = true
  try {
    const operations = new McpOperationClient(browser)
    const result = await operations.list(1, 'PREPARED')
    const candidates = result.items.filter(item => /^[0-9a-f]{32}$/.test(item.operationId)
      && item.state === 'PREPARED' && item.canConfirm === true && !wasOpened(item.operationId))
    for (const candidate of candidates.slice(0, 5)) {
      if (unmounted) return
      let detail: McpOperationView
      try {
        detail = await operations.view(candidate.operationId)
      } catch {
        // 授权已失效或对象不可见：跳过该候选，不自动打开错误页。
        continue
      }
      if (unmounted) return
      if (detail.state !== 'PREPARED' || detail.canConfirm !== true) continue
      markOpened(candidate.operationId)
      if (!openConfirmWindow(confirmPath(candidate.operationId))) {
        pendingOperationId.value = candidate.operationId
        pendingBanner.value = '检测到待确认的写操作，浏览器未能自动打开确认页，可点击前往确认。'
        return
      }
      return
    }
  } catch {
    // 自动打开失败不影响列表；用户仍可从「去确认」进入。
  }
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
    await autoOpenIfNeeded()
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
