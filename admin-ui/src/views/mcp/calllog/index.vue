<template>
  <div class="app-container">
    <div class="mb8">
      <el-button :loading="statisticsLoading" @click="loadStatistics">刷新运行统计</el-button>
      <el-alert v-if="statisticsError" title="运行统计读取失败，请稍后重试。" type="error" :closable="false" />
      <template v-if="statistics">
        <p>本进程自启动以来：审计已写入 {{ statistics.audit.written }}，失败 {{ statistics.audit.failed }}，丢弃 {{ statistics.audit.dropped }}，排队 {{ statistics.audit.queued }}。</p>
        <p>当前工具执行 {{ statistics.execution.running }}/{{ statistics.execution.limit }}；每用户并发上限 {{ statistics.execution.perUserLimit }}，执行中用户 {{ statistics.execution.activeUsers }}。</p>
        <el-alert v-if="statistics.audit.failed > 0 || statistics.audit.dropped > 0" title="本进程曾发生审计写入失败或丢弃，调用列表可能不完整，请排查数据库和告警日志。" type="warning" :closable="false" />
      </template>
    </div>
    <el-form :model="query" :inline="true" @submit.prevent="search">
      <el-form-item label="用户 ID"><el-input-number v-model="query.userId" :min="1" :precision="0" :controls="false" /></el-form-item>
      <el-form-item label="客户端"><el-input v-model="query.keyword" clearable maxlength="128" @keyup.enter="search" /></el-form-item>
      <el-form-item label="工具"><el-input v-model="query.toolName" clearable maxlength="64" @keyup.enter="search" /></el-form-item>
      <el-form-item label="结果码"><el-input v-model="query.resultCode" clearable maxlength="64" @keyup.enter="search" /></el-form-item>
      <el-form-item><el-button type="primary" @click="search">搜索</el-button><el-button @click="reset">重置</el-button></el-form-item>
    </el-form>
    <el-table :data="rows" v-loading="loading" row-key="logId" empty-text="暂无调用记录">
      <el-table-column prop="createdAt" label="时间" min-width="200" />
      <el-table-column prop="eventType" label="事件" min-width="150" />
      <el-table-column prop="userId" label="用户 ID" width="100" />
      <el-table-column prop="clientName" label="客户端" min-width="160" />
      <el-table-column prop="toolName" label="工具" min-width="200" />
      <el-table-column prop="resultCode" label="结果码" min-width="160" />
      <el-table-column prop="elapsedMs" label="耗时（毫秒）" width="130" />
      <el-table-column label="操作" width="80"><template #default="{ row }"><el-button link type="primary" v-hasPermi="['mcp:calllog:query']" @click="detail(row)">详情</el-button></template></el-table-column>
    </el-table>
    <pagination v-show="total > 0" :total="total" v-model:page="query.pageNum" v-model:limit="query.pageSize" @pagination="load" />
    <AdminDetail v-model="open" title="调用元数据" :fields="fields" />
  </div>
</template>
<script setup lang="ts" name="McpCalllog">
import { ref, reactive } from 'vue'
import { listMcpCalls, getMcpCall, getMcpStatistics } from '@/api/mcp/management'
import type { McpAdminCall, McpAdminQuery, McpAdminStatistics } from '@/types'
import AdminDetail from '../components/AdminDetail.vue'
const query = reactive<McpAdminQuery>({ pageNum: 1, pageSize: 20 })
const rows = ref<McpAdminCall[]>([]), total = ref(0), loading = ref(false), open = ref(false)
const fields = ref<Record<string, unknown>>({})
const statistics = ref<McpAdminStatistics>()
const statisticsLoading = ref(false), statisticsError = ref(false)
let loadVersion = 0
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
    fields.value = { '记录 ID': call.logId, '请求 ID': call.requestId, '事件': call.eventType, '用户 ID': call.userId,
      '授权 ID': call.grantId, '客户端': call.clientName, '客户端标识': call.clientId, '工具': call.toolName,
      '结果码': call.resultCode, '耗时（毫秒）': call.elapsedMs, '时间': call.createdAt }
    open.value = true
  } catch { /* 请求层已显示后台错误。 */ }
}
void load()
void loadStatistics()
</script>
