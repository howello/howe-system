<template>
  <main class="mcp-page">
    <section class="mcp-card mcp-card--wide" aria-labelledby="connections-title">
      <McpBrand badge="我的连接" />

      <h1 id="connections-title">我的 MCP 连接</h1>
      <p class="mcp-intro">这里是当前账号授权过的 AI 客户端。撤销后，该客户端需要重新获得你的授权才能继续操作。</p>

      <div class="mcp-userbar">
        <div class="mcp-userbar__who">
          <span class="mcp-userbar__avatar" aria-hidden="true">{{ avatarText }}</span>
          <span>当前账号 <span class="mcp-userbar__name">{{ username || '读取中…' }}</span></span>
          <span class="mcp-state" :class="disabled ? 'mcp-state--off' : 'mcp-state--on'">
            {{ disabled ? 'MCP 暂停接受调用' : 'MCP 正常' }}
          </span>
        </div>
        <div class="mcp-userbar__actions">
          <el-button size="small" :icon="Refresh" :disabled="busy" @click="load">刷新</el-button>
          <el-button size="small" :icon="SwitchButton" :disabled="busy || !client" @click="logout">退出 MCP 登录</el-button>
        </div>
      </div>

      <el-alert v-if="error" :title="error" type="error" :closable="false" class="mcp-error" role="alert" show-icon />
      <el-alert
        v-if="disabled"
        title="MCP 暂停接受调用，仍可查看和撤销已有连接。"
        type="warning"
        :closable="false"
        show-icon
        class="mcp-error"
      />

      <div class="mcp-summary">
        <div class="mcp-summary__item">
          <div class="mcp-summary__label">授权连接</div>
          <div class="mcp-summary__value">{{ connectionTotal }}</div>
          <div class="mcp-summary__hint">本页有效 {{ activeOnPage }} 条</div>
        </div>
        <div class="mcp-summary__item">
          <div class="mcp-summary__label">调用记录</div>
          <div class="mcp-summary__value">{{ callTotal }}</div>
          <div class="mcp-summary__hint">仅元数据，不含请求正文</div>
        </div>
        <div class="mcp-summary__item">
          <div class="mcp-summary__label">最近调用</div>
          <div class="mcp-summary__value mcp-summary__value--time">{{ latestCall }}</div>
          <div class="mcp-summary__hint">本页最近一条记录</div>
        </div>
      </div>

      <section class="mcp-section">
        <div class="mcp-section__head">
          <div>
            <h2 class="mcp-section__title">授权的客户端</h2>
            <p class="mcp-section__desc">撤销只影响该客户端，不会注销你的系统账号。</p>
          </div>
        </div>
        <div class="mcp-section__body">
          <el-table :data="connections" v-loading="busy" empty-text="暂无连接" row-key="grantId">
            <el-table-column label="客户端" min-width="190">
              <template #default="{ row }">
                <div class="mcp-client-cell">
                  <span class="mcp-client-cell__icon"><el-icon><Connection /></el-icon></span>
                  <div>
                    <div class="mcp-client-cell__name">{{ row.clientName }}</div>
                    <div class="mcp-client-cell__id mcp-mono">{{ row.clientId }}</div>
                  </div>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="110">
              <template #default="{ row }">
                <span class="mcp-state" :class="isActive(row) ? 'mcp-state--on' : 'mcp-state--off'">
                  {{ isActive(row) ? '已授权' : '已撤销或停用' }}
                </span>
              </template>
            </el-table-column>
            <el-table-column label="授权范围" min-width="200">
              <template #default="{ row }">
                <div class="mcp-scope-chips">
                  <span v-for="scope in row.scopes" :key="scope" class="mcp-scope-chip">{{ scopeLabel(scope) }}</span>
                  <span v-if="!row.scopes.length" class="mcp-muted">无</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="最近调用" min-width="170">
              <template #default="{ row }"><span class="mcp-time">{{ formatTime(row.lastUsedAt) }}</span></template>
            </el-table-column>
            <el-table-column label="操作" width="90" align="center">
              <template #default="{ row }">
                <el-button
                  type="danger"
                  link
                  :icon="CircleClose"
                  :disabled="busy || row.status !== '0'"
                  @click="revoke(row)"
                >撤销</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
        <div v-if="connectionTotal > 20" class="mcp-section__foot">
          <el-pagination
            v-model:current-page="connectionPage"
            :total="connectionTotal"
            :page-size="20"
            layout="prev, pager, next"
            background
            @current-change="load"
          />
        </div>
      </section>

      <section class="mcp-section">
        <div class="mcp-section__head">
          <div>
            <h2 class="mcp-section__title">调用记录</h2>
            <p class="mcp-section__desc">只记录事件、工具与结果码，不保存入参和返回正文。</p>
          </div>
        </div>
        <div class="mcp-section__body">
          <el-table :data="calls" v-loading="busy" empty-text="暂无调用记录" row-key="logId">
            <el-table-column label="时间" min-width="170">
              <template #default="{ row }"><span class="mcp-time">{{ formatTime(row.createdAt) }}</span></template>
            </el-table-column>
            <el-table-column label="事件" min-width="120">
              <template #default="{ row }"><span class="mcp-scope-chip">{{ eventLabel(row.eventType) }}</span></template>
            </el-table-column>
            <el-table-column label="工具" min-width="170">
              <template #default="{ row }"><span class="mcp-mono">{{ row.toolName || '—' }}</span></template>
            </el-table-column>
            <el-table-column label="结果" min-width="150">
              <template #default="{ row }">
                <el-tag :type="resultType(row.resultCode)" effect="plain" size="small">{{ row.resultCode }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="耗时" width="100" align="right">
              <template #default="{ row }"><span class="mcp-mono">{{ row.elapsedMs }} ms</span></template>
            </el-table-column>
          </el-table>
        </div>
        <div v-if="callTotal > 20" class="mcp-section__foot">
          <el-pagination
            v-model:current-page="callPage"
            :total="callTotal"
            :page-size="20"
            layout="prev, pager, next"
            background
            @current-change="load"
          />
        </div>
      </section>
    </section>
    <p class="mcp-page__foot">撤销连接不会删除历史调用记录，便于后续追溯。</p>
  </main>
</template>

<script setup lang="ts" name="McpConnections">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { CircleClose, Connection, Refresh, SwitchButton } from '@element-plus/icons-vue'
import { createMcpBrowserClient, mcpErrorMessage, McpBrowserError, type McpBrowserClient } from '@/api/mcp/browser'
import type { McpConnection, McpCall } from '@/types'
import McpBrand from './components/McpBrand.vue'
import './style.css'

const router = useRouter()
const connections = ref<McpConnection[]>([]), calls = ref<McpCall[]>([])
const connectionPage = ref(1), callPage = ref(1), connectionTotal = ref(0), callTotal = ref(0)
const username = ref(''), error = ref(''), busy = ref(false), disabled = ref(false)
let client: McpBrowserClient | undefined
/** 固定事件类型使用中文展示，未知类型保留原始代码。 */
const EVENT_LABELS: Record<string, string> = {
  AUTHORIZE: '授权确认', TOKEN_EXCHANGE: '换取令牌', TOKEN_REVOKE: '撤销令牌', GRANT_REVOKE: '撤销授权',
  CLIENT_REGISTER: '客户端登记', LOGIN: '登录', LOGOUT: '退出', PROTOCOL: '协议请求', TOOL_CALL: '工具调用'
}
/** 账号头像占位取首个字符。 */
const avatarText = computed(() => (username.value || 'M').slice(0, 1).toUpperCase())
/** 当前页仍有效的连接数量。 */
const activeOnPage = computed(() => connections.value.filter(isActive).length)
/** 本页最近的调用时间，空列表显示占位。 */
const latestCall = computed(() => calls.value.length ? formatTime(calls.value[0].createdAt) : '暂无')
/** 连接与客户端都正常才算有效。 */
function isActive(connection: McpConnection): boolean { return connection.status === '0' && connection.clientStatus === '0' }
/** 已知范围使用中文含义。 */
function scopeLabel(scope: string): string {
  return scope === 'mcp:connect' ? '连接工具' : scope === 'offline_access' ? '离线访问' : scope
}
/** 已知事件给出中文含义。 */
function eventLabel(event: string): string { return EVENT_LABELS[event] || event }
/** 结果码只按语义分组着色。 */
function resultType(code: string): 'success' | 'warning' | 'danger' | 'info' {
  if (code === 'OK' || code === 'ACCEPTED') return 'success'
  if (code === 'PENDING') return 'info'
  if (['RATE_LIMITED', 'SERVER_BUSY', 'USER_BUSY', 'TOOL_TIMEOUT', 'UNAVAILABLE'].includes(code)) return 'warning'
  return 'danger'
}
/** 日期为空时保留无记录语义。 */
function formatTime(value: string | null): string { return value ? new Date(value).toLocaleString() : '暂无' }
/** 会话失效只清空当前页面并转到独立登录。 */
async function handleError(cause: unknown): Promise<void> {
  if (cause instanceof McpBrowserError && cause.status === 401) {
    connections.value = []; calls.value = []; await router.replace('/mcp/login')
  } else error.value = mcpErrorMessage(cause)
}
/** 两个列表始终由服务器根据本人会话查询。 */
async function load(): Promise<void> {
  if (!client || busy.value) return
  busy.value = true; error.value = ''
  try {
    const [connectionResult, callResult] = await Promise.all([client.connections(connectionPage.value), client.calls(callPage.value)])
    connections.value = connectionResult.items; connectionTotal.value = connectionResult.total
    calls.value = callResult.items; callTotal.value = callResult.total
  } catch (cause) { await handleError(cause) }
  finally { busy.value = false }
}
/** 二次确认后执行本人授权撤销，不在取消弹窗时发送请求。 */
async function revoke(connection: McpConnection): Promise<void> {
  if (!client || busy.value) return
  try { await ElMessageBox.confirm(`撤销“${connection.clientName}”的访问授权？`, '撤销连接', { confirmButtonText: '撤销', cancelButtonText: '取消', type: 'warning' }) }
  catch { return }
  busy.value = true; error.value = ''
  try { await client.revoke(connection.grantId) }
  catch (cause) { await handleError(cause) }
  finally { busy.value = false }
  await load()
}
/** 退出不撤销授权，也不清除管理端账号。 */
async function logout(): Promise<void> {
  if (!client || busy.value) return
  busy.value = true
  try { await client.logout(); await router.replace('/mcp/login') }
  catch (cause) { await handleError(cause) }
  finally { busy.value = false }
}
/** 独立获取当前账号，不调用后台 getInfo 或菜单接口。 */
async function initialize(): Promise<void> {
  try {
    const result = await createMcpBrowserClient(); client = result.client; disabled.value = !result.configuration.enabled
    username.value = (await client.session()).username
    await load()
  } catch (cause) { await handleError(cause) }
}
void initialize()
</script>

<style scoped>
.mcp-summary__value--time {
  font-size: 14px;
  font-weight: 600;
  line-height: 1.5;
  white-space: normal;
}

.mcp-client-cell {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.mcp-client-cell__icon {
  display: grid;
  flex: none;
  width: 32px;
  height: 32px;
  place-items: center;
  border-radius: 9px;
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
}

.mcp-client-cell__name {
  color: var(--el-text-color-primary);
  font-weight: 600;
  overflow-wrap: anywhere;
}

.mcp-client-cell__id {
  margin-top: 2px;
  color: var(--el-text-color-secondary);
  font-size: 11px;
  overflow-wrap: anywhere;
}
</style>
