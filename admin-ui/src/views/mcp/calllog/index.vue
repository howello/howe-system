<template>
  <div class="app-container mcp-admin">
    <McpPageHeader
      title="调用记录"
      eyebrow="审计元数据"
      description="只记录固定事件与结果码，不保存请求、响应正文或任何凭据；统计为本进程自启动以来的数字。"
    >
      <template #icon><el-icon><List /></el-icon></template>
      <template #actions>
        <el-button :icon="DataAnalysis" :loading="statisticsLoading" @click="loadStatistics">刷新运行统计</el-button>
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新列表</el-button>
      </template>
    </McpPageHeader>

    <section class="mcp-panel">
      <div class="mcp-panel__head">
        <div>
          <h2 class="mcp-panel__title">运行统计</h2>
          <p class="mcp-panel__desc">仅当前进程，不含用户标识，不是集群总数。</p>
        </div>
        <div class="mcp-panel__toolbar">
          <span v-if="statistics" class="mcp-status" :class="{ 'mcp-status--off': statistics.audit.failed > 0 || statistics.audit.dropped > 0 }">
            {{ statistics.audit.failed > 0 || statistics.audit.dropped > 0 ? '存在审计异常' : '审计写入正常' }}
          </span>
        </div>
      </div>
      <div class="mcp-panel__body">
        <el-alert v-if="statisticsError" title="运行统计读取失败，请稍后重试。" type="error" :closable="false" show-icon />
        <template v-else-if="!statistics">
          <div class="mcp-loading">正在读取运行统计…</div>
        </template>
        <template v-else>
          <div class="mcp-stat-grid">
            <div class="mcp-stat">
              <div class="mcp-stat__label">审计已写入</div>
              <div class="mcp-stat__value">{{ statistics.audit.written }}</div>
              <div class="mcp-stat__hint">排队 {{ statistics.audit.queued }} · 丢弃 {{ statistics.audit.dropped }}</div>
            </div>
            <div class="mcp-stat" :class="{ 'mcp-stat--warn': statistics.audit.failed > 0 }">
              <div class="mcp-stat__label">审计失败</div>
              <div class="mcp-stat__value">{{ statistics.audit.failed }}</div>
              <div class="mcp-stat__hint">失败时调用列表可能不完整</div>
            </div>
            <div class="mcp-stat">
              <div class="mcp-stat__label">工具执行中</div>
              <div class="mcp-stat__value">{{ statistics.execution.running }}<span class="mcp-stat__hint"> / {{ statistics.execution.limit }}</span></div>
              <div class="mcp-stat__hint">每用户上限 {{ statistics.execution.perUserLimit }}</div>
            </div>
            <div class="mcp-stat">
              <div class="mcp-stat__label">执行中用户</div>
              <div class="mcp-stat__value">{{ statistics.execution.activeUsers }}</div>
              <div class="mcp-stat__hint">工具容量 {{ statistics.execution.closed ? '已关闭' : '可用' }}</div>
            </div>
          </div>
          <el-alert
            v-if="statistics.audit.failed > 0 || statistics.audit.dropped > 0"
            title="本进程曾发生审计写入失败或丢弃，调用列表可能不完整，请排查数据库和告警日志。"
            type="warning"
            :closable="false"
            show-icon
          />
        </template>
      </div>
    </section>

    <section class="mcp-filter">
      <el-form :model="query" :inline="true" @submit.prevent="search">
        <el-form-item label="用户 ID">
          <el-input-number v-model="query.userId" :min="1" :precision="0" :controls="false" />
        </el-form-item>
        <el-form-item label="客户端">
          <el-input v-model="query.keyword" clearable maxlength="128" style="width: 180px" @keyup.enter="search" />
        </el-form-item>
        <el-form-item label="工具">
          <el-input v-model="query.toolName" clearable maxlength="64" style="width: 180px" @keyup.enter="search" />
        </el-form-item>
        <el-form-item label="结果码">
          <el-input v-model="query.resultCode" clearable maxlength="64" style="width: 170px" @keyup.enter="search" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="search">搜索</el-button>
          <el-button :icon="Refresh" @click="reset">重置</el-button>
        </el-form-item>
      </el-form>
    </section>

    <section class="mcp-panel mcp-table">
      <div class="mcp-panel__head">
        <div>
          <h2 class="mcp-panel__title">调用事件</h2>
          <p class="mcp-panel__desc">共 {{ total }} 条；点击详情查看已脱敏的固定元数据。</p>
        </div>
      </div>
      <div class="mcp-panel__body mcp-panel__body--flush">
        <el-table :data="rows" v-loading="loading" row-key="logId" empty-text="暂无调用记录">
          <el-table-column prop="createdAt" label="时间" min-width="180" />
          <el-table-column label="事件" min-width="150">
            <template #default="{ row }"><span class="mcp-chip">{{ eventLabel(row.eventType) }}</span></template>
          </el-table-column>
          <el-table-column prop="userId" label="用户 ID" width="96" />
          <el-table-column prop="clientName" label="客户端" min-width="150" show-overflow-tooltip />
          <el-table-column label="工具" min-width="180" show-overflow-tooltip>
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
          <el-table-column label="操作" width="80" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" :icon="View" v-hasPermi="['mcp:calllog:query']" @click="detail(row)">详情</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
      <div v-if="total > 0" class="mcp-panel__foot">
        <pagination :total="total" v-model:page="query.pageNum" v-model:limit="query.pageSize" @pagination="load" />
      </div>
    </section>

    <AdminDetail v-model="open" title="调用元数据" :fields="fields" />
  </div>
</template>

<script setup lang="ts" name="McpCalllog">
import { ref, reactive } from 'vue'
import { DataAnalysis, List, Refresh, Search, View } from '@element-plus/icons-vue'
import { listMcpCalls, getMcpCall, getMcpStatistics } from '@/api/mcp/management'
import type { McpAdminCall, McpAdminQuery, McpAdminStatistics } from '@/types'
import AdminDetail from '../components/AdminDetail.vue'
import McpPageHeader from '../components/McpPageHeader.vue'
import '../admin.css'

const query = reactive<McpAdminQuery>({ pageNum: 1, pageSize: 20 })
const rows = ref<McpAdminCall[]>([]), total = ref(0), loading = ref(false), open = ref(false)
const fields = ref<Record<string, unknown>>({})
const statistics = ref<McpAdminStatistics>()
const statisticsLoading = ref(false), statisticsError = ref(false)
let loadVersion = 0
/** 固定事件类型使用中文展示，未知类型保留原始代码。 */
const EVENT_LABELS: Record<string, string> = {
  AUTHORIZE: '授权确认', TOKEN_EXCHANGE: '换取令牌', TOKEN_REVOKE: '撤销令牌', GRANT_REVOKE: '撤销授权',
  CLIENT_REGISTER: '客户端登记', LOGIN: '登录', LOGOUT: '退出', PROTOCOL: '协议请求', TOOL_CALL: '工具调用'
}
/** 结果码只按语义分组着色，具体代码仍展示给管理员。 */
function resultType(code: string): 'success' | 'warning' | 'danger' | 'info' {
  if (code === 'OK' || code === 'ACCEPTED') return 'success'
  if (code === 'PENDING') return 'info'
  if (['RATE_LIMITED', 'SERVER_BUSY', 'USER_BUSY', 'TOOL_TIMEOUT', 'UNAVAILABLE'].includes(code)) return 'warning'
  return 'danger'
}
/** 已知事件给出中文含义。 */
function eventLabel(event: string): string { return EVENT_LABELS[event] || event }
/** 只按需读取本进程指标，不启动跨页面后台轮询。 */
async function loadStatistics(): Promise<void> {
  if (statisticsLoading.value) return
  statisticsLoading.value = true; statisticsError.value = false
  try { statistics.value = (await getMcpStatistics()).data }
  catch { statistics.value = undefined; statisticsError.value = true }
  finally { statisticsLoading.value = false }
}
/** 查询只包含审计白名单元数据。 */
async function load(): Promise<void> {
  const version = ++loadVersion
  loading.value = true
  try {
    const result = await listMcpCalls({ ...query })
    if (version === loadVersion) { rows.value = result.rows; total.value = result.total }
  } catch { if (version === loadVersion) { rows.value = []; total.value = 0 } }
  finally { if (version === loadVersion) loading.value = false }
}
/** 修改条件回到第一页。 */
function search(): void { query.pageNum = 1; void load() }
/** 清空筛选，不发删除或清理请求。 */
function reset(): void { query.userId = undefined; query.keyword = undefined; query.toolName = undefined; query.resultCode = undefined; search() }
/** 不提供查看原始请求、响应或凭据的入口。 */
async function detail(row: McpAdminCall): Promise<void> {
  try {
    const call = (await getMcpCall(row.logId)).data!
    fields.value = { '记录 ID': call.logId, '请求 ID': call.requestId, '事件': eventLabel(call.eventType), '用户 ID': call.userId,
      '授权 ID': call.grantId, '客户端': call.clientName, '客户端标识': call.clientId, '工具': call.toolName,
      '结果码': call.resultCode, '耗时（毫秒）': call.elapsedMs, '时间': call.createdAt }
    open.value = true
  } catch { /* 请求层已显示后台错误。 */ }
}
void load()
void loadStatistics()
</script>
