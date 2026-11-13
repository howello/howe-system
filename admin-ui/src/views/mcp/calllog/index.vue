<template>
  <div class="app-container">
    <el-card shadow="never" class="mb8">
      <template #header>
        <div class="mcp-card-head">
          <span>运行统计</span>
          <el-tag v-if="statistics" :type="statistics.audit.failed > 0 || statistics.audit.dropped > 0 ? 'warning' : 'success'" effect="light">
            {{ statistics.audit.failed > 0 || statistics.audit.dropped > 0 ? '存在审计异常' : '审计写入正常' }}
          </el-tag>
        </div>
      </template>
      <el-alert
        v-if="statisticsError"
        title="运行统计读取失败，请稍后重试。"
        type="error"
        :closable="false"
        show-icon
      />
      <div v-else-if="!statistics" class="mcp-loading">正在读取运行统计…</div>
      <el-row v-else :gutter="12">
        <el-col :xs="12" :sm="6">
          <el-statistic title="审计已写入" :value="statistics.audit.written">
            <template #suffix><span class="mcp-stat-hint">排队 {{ statistics.audit.queued }} · 丢弃 {{ statistics.audit.dropped }}</span></template>
          </el-statistic>
        </el-col>
        <el-col :xs="12" :sm="6">
          <el-statistic title="审计失败" :value="statistics.audit.failed" />
        </el-col>
        <el-col :xs="12" :sm="6">
          <el-statistic title="工具执行中" :value="statistics.execution.running">
            <template #suffix><span class="mcp-stat-hint">/ {{ statistics.execution.limit }}，每用户 {{ statistics.execution.perUserLimit }}</span></template>
          </el-statistic>
        </el-col>
        <el-col :xs="12" :sm="6">
          <el-statistic title="执行中用户" :value="statistics.execution.activeUsers">
            <template #suffix><span class="mcp-stat-hint">工具容量{{ statistics.execution.closed ? '已关闭' : '可用' }}</span></template>
          </el-statistic>
        </el-col>
      </el-row>
      <el-alert
        v-if="statistics && (statistics.audit.failed > 0 || statistics.audit.dropped > 0)"
        title="本进程曾发生审计写入失败或丢弃，调用列表可能不完整，请排查数据库和告警日志。"
        type="warning"
        :closable="false"
        show-icon
        class="mcp-stat-alert"
      />
      <p class="mcp-stat-note">仅当前进程，不含用户标识，不是集群总数。</p>
    </el-card>

    <el-form :model="query" :inline="true" v-show="showSearch" label-width="80px">
      <el-form-item label="用户 ID" prop="userId">
        <el-input-number v-model="query.userId" :min="1" :precision="0" :controls="false" style="width: 140px" />
      </el-form-item>
      <el-form-item label="客户端" prop="keyword">
        <el-input v-model="query.keyword" clearable maxlength="128" style="width: 200px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="工具" prop="toolName">
        <el-input v-model="query.toolName" clearable maxlength="64" style="width: 200px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="结果码" prop="resultCode">
        <el-input v-model="query.resultCode" clearable maxlength="64" style="width: 190px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button plain icon="Refresh" :loading="loading" @click="getList">刷新列表</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button plain icon="DataAnalysis" :loading="statisticsLoading" @click="loadStatistics">刷新运行统计</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="rows" row-key="logId" empty-text="暂无调用记录">
      <el-table-column prop="createdAt" label="时间" min-width="180" />
      <el-table-column label="事件" min-width="150">
        <template #default="{ row }"><el-tag effect="plain" size="small">{{ eventLabel(row.eventType) }}</el-tag></template>
      </el-table-column>
      <el-table-column prop="userId" label="用户 ID" width="96" />
      <el-table-column prop="clientName" label="客户端" min-width="150" show-overflow-tooltip />
      <el-table-column label="工具" min-width="180" show-overflow-tooltip>
        <template #default="{ row }"><code>{{ row.toolName || '—' }}</code></template>
      </el-table-column>
      <el-table-column label="结果" min-width="180">
        <template #default="{ row }">
          <el-tooltip :content="row.resultCode" placement="top">
            <el-tag :type="mcpResultCodeType(row.resultCode)" effect="plain" size="small">{{ mcpResultCodeLabel(row.resultCode) }}</el-tag>
          </el-tooltip>
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

    <pagination
      v-show="total > 0"
      :total="total"
      v-model:page="query.pageNum"
      v-model:limit="query.pageSize"
      @pagination="getList"
    />

    <el-dialog v-model="open" title="调用元数据" width="min(780px, 94vw)" append-to-body>
      <el-descriptions :column="1" border>
        <el-descriptions-item v-for="(value, label) in fields" :key="label" :label="label">{{ display(value) }}</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="open = false">关 闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="McpCalllog">
import { reactive, ref } from 'vue'
import { DataAnalysis, View } from '@element-plus/icons-vue'
import { listMcpCalls, getMcpCall, getMcpStatistics } from '@/api/mcp/management'
import type { McpAdminCall, McpAdminQuery, McpAdminStatistics } from '@/types'
import { mcpResultCodeLabel, mcpResultCodeType } from '@/utils/mcpResultCodes'

const showSearch = ref(true)
const query = reactive<McpAdminQuery>({ pageNum: 1, pageSize: 20 })
const rows = ref<McpAdminCall[]>([]), total = ref(0), loading = ref(false), open = ref(false)
const fields = ref<Record<string, unknown>>({})
const statistics = ref<McpAdminStatistics>()
const statisticsLoading = ref(false), statisticsError = ref(false)

/** 固定事件类型使用中文展示，未知类型保留原始代码。 */
const EVENT_LABELS: Record<string, string> = {
  AUTHORIZE: '授权确认', TOKEN_EXCHANGE: '换取令牌', TOKEN_REVOKE: '撤销令牌', GRANT_REVOKE: '撤销授权',
  CLIENT_REGISTER: '客户端登记', LOGIN: '登录', LOGOUT: '退出', PROTOCOL: '协议请求', TOOL_CALL: '工具调用'
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
async function getList(): Promise<void> {
  loading.value = true
  try {
    const result = await listMcpCalls({ ...query })
    rows.value = result.rows
    total.value = result.total
  } catch { rows.value = []; total.value = 0 }
  finally { loading.value = false }
}
/** 修改条件回到第一页。 */
function handleQuery(): void { query.pageNum = 1; void getList() }
/** 清空筛选，不发删除或清理请求。 */
function resetQuery(): void {
  query.userId = undefined; query.keyword = undefined; query.toolName = undefined; query.resultCode = undefined
  handleQuery()
}
/** 不提供查看原始请求、响应或凭据的入口。 */
async function detail(row: McpAdminCall): Promise<void> {
  try {
    const call = (await getMcpCall(row.logId)).data!
    fields.value = { '记录 ID': call.logId, '请求 ID': call.requestId, '事件': eventLabel(call.eventType), '用户 ID': call.userId,
      '授权 ID': call.grantId, '客户端': call.clientName, '客户端标识': call.clientId, '工具': call.toolName,
      '结果码': call.resultCode, '结果说明': mcpResultCodeLabel(call.resultCode), '耗时（毫秒）': call.elapsedMs, '时间': call.createdAt }
    open.value = true
  } catch { /* 请求层已显示后台错误。 */ }
}
/** 文本插值展示，空值使用占位。 */
function display(value: unknown): string {
  if (value === null || value === undefined || value === '') return '—'
  return typeof value === 'object' ? JSON.stringify(value, null, 2) : String(value)
}

void getList()
void loadStatistics()
</script>

<style scoped>
.mcp-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.mcp-loading {
  padding: 16px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  text-align: center;
}
.mcp-stat-hint {
  margin-left: 6px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.mcp-stat-alert {
  margin-top: 14px;
}
.mcp-stat-note {
  margin: 12px 0 0;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.mcp-mono {
  font-family: ui-monospace, SFMono-Regular, Consolas, 'Liberation Mono', monospace;
  font-size: 12px;
}
</style>
