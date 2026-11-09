<template>
  <div class="app-container">
    <el-alert title="客户端自动接入后显示在此。停用将撤销旧授权，恢复后用户需重新授权。" type="info" :closable="false" class="mb8" />
    <div class="mb8">
      <el-button :loading="configurationLoading" @click="loadConfiguration">刷新运行配置</el-button>
      <el-alert v-if="configurationError" title="运行配置读取失败，无法确认地址是否已生效。" type="error" :closable="false" />
      <template v-if="configuration">
        <el-alert v-if="!configuration.configuredValid" title="在线地址配置无效，请在参数设置中修正后再重启；当前仍使用下方已生效地址。" type="error" :closable="false" />
        <el-alert v-else-if="configuration.restartRequired" title="在线地址已修改，需重启后端生效。当前连接继续使用下方运行地址。" type="warning" :closable="false" />
        <el-descriptions :column="1" border class="mt8">
          <el-descriptions-item label="当前运行开关">{{ configuration.enabled ? '开启' : '关闭' }}</el-descriptions-item>
          <el-descriptions-item label="当前 MCP 连接地址">{{ configuration.active.resource }}</el-descriptions-item>
          <el-descriptions-item label="当前 OAuth issuer">{{ configuration.active.issuer }}</el-descriptions-item>
          <el-descriptions-item label="当前授权页面站点">{{ configuration.active.frontendBaseUrl }}</el-descriptions-item>
          <template v-if="configuration.restartRequired && configuration.configured">
            <el-descriptions-item label="保存的后端地址（重启后生效）">{{ configuration.configured.issuer }}</el-descriptions-item>
            <el-descriptions-item label="保存的前端地址（重启后生效）">{{ configuration.configured.frontendBaseUrl }}</el-descriptions-item>
          </template>
        </el-descriptions>
      </template>
    </div>
    <el-form :inline="true" :model="query" @submit.prevent="search">
      <el-form-item label="客户端"><el-input v-model="query.keyword" placeholder="名称或客户端标识" clearable maxlength="128" @keyup.enter="search" /></el-form-item>
      <el-form-item label="状态"><el-select v-model="query.status" placeholder="全部" clearable style="width: 140px"><el-option label="正常" value="0" /><el-option label="停用" value="1" /></el-select></el-form-item>
      <el-form-item><el-button type="primary" @click="search">搜索</el-button><el-button @click="reset">重置</el-button></el-form-item>
    </el-form>
    <el-table :data="rows" v-loading="loading" row-key="id" empty-text="暂无客户端">
      <el-table-column prop="clientName" label="名称" min-width="160" />
      <el-table-column prop="clientId" label="客户端标识" min-width="220" show-overflow-tooltip />
      <el-table-column prop="registrationSource" label="登记方式" width="100" />
      <el-table-column label="状态" width="90"><template #default="{ row }"><el-tag :type="row.status === '0' ? 'success' : 'info'">{{ row.status === '0' ? '正常' : '停用' }}</el-tag></template></el-table-column>
      <el-table-column label="元数据" width="110"><template #default="{ row }">{{ row.registrationSource !== 'CIMD' ? '不适用' : row.metadataValid === 'Y' ? '有效' : '验证失败' }}</template></el-table-column>
      <el-table-column prop="grantCount" label="有效授权数" width="110" />
      <el-table-column prop="createdAt" label="登记时间" min-width="190" />
      <el-table-column label="操作" fixed="right" width="170"><template #default="{ row }">
        <el-button link type="primary" v-hasPermi="['mcp:client:query']" @click="detail(row)">详情</el-button>
        <el-button link :type="row.status === '0' ? 'danger' : 'primary'" :disabled="changing" v-hasPermi="['mcp:client:edit']" @click="changeStatus(row)">{{ row.status === '0' ? '停用' : '恢复' }}</el-button>
      </template></el-table-column>
    </el-table>
    <pagination v-show="total > 0" :total="total" v-model:page="query.pageNum" v-model:limit="query.pageSize" @pagination="load" />
    <AdminDetail v-model="open" title="客户端公开详情" :fields="fields" />
  </div>
</template>
<script setup lang="ts" name="McpClient">
import { ref, reactive } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listMcpClients, getMcpClient, setMcpClientStatus, getMcpConfiguration } from '@/api/mcp/management'
import type { McpAdminClient, McpAdminQuery, McpAdminConfiguration } from '@/types'
import AdminDetail from '../components/AdminDetail.vue'
const query = reactive<McpAdminQuery>({ pageNum: 1, pageSize: 20 })
const rows = ref<McpAdminClient[]>([]), total = ref(0), loading = ref(false), changing = ref(false), open = ref(false)
const fields = ref<Record<string, unknown>>({})
const configuration = ref<McpAdminConfiguration>()
const configurationLoading = ref(false), configurationError = ref(false)
let loadVersion = 0
/** 运行状态独立刷新；失败时清空旧快照，不把旧数据当成当前配置。 */
async function loadConfiguration(): Promise<void> {
  if (configurationLoading.value) return
  configurationLoading.value = true; configurationError.value = false
  try { configuration.value = (await getMcpConfiguration()).data }
  catch { configuration.value = undefined; configurationError.value = true }
  finally { configurationLoading.value = false }
}
/** 获取后台分页列表，失败时不留下上次查询的误导数据。 */
async function load(): Promise<void> {
  const version = ++loadVersion
  loading.value = true
  try {
    const result = await listMcpClients({ ...query })
    if (version === loadVersion) { rows.value = result.rows; total.value = result.total }
  } catch { if (version === loadVersion) { rows.value = []; total.value = 0 } }
  finally { if (version === loadVersion) loading.value = false }
}
/** 查询条件变更从第一页开始。 */
function search(): void { query.pageNum = 1; void load() }
/** 清空可选筛选条件。 */
function reset(): void { query.keyword = undefined; query.status = undefined; search() }
/** 详情展示公开协议字段，不能修改回调。 */
async function detail(row: McpAdminClient): Promise<void> {
  try {
    const result = (await getMcpClient(row.id)).data!
    fields.value = { '名称': result.client.clientName, '客户端标识': result.client.clientId, '登记方式': result.client.registrationSource,
      '状态': result.client.status === '0' ? '正常' : '停用', '回调地址': result.redirectUris, '认证方式': result.authenticationMethods,
      '授权类型': result.grantTypes, '允许申请范围': result.scopes, '登记时间': result.client.createdAt, '更新时间': result.client.updatedAt }
    open.value = true
  } catch { /* 请求层已显示后台错误。 */ }
}
/** 停用和恢复均须用户明确确认；取消不发请求。 */
async function changeStatus(row: McpAdminClient): Promise<void> {
  if (changing.value) return
  const stopped = row.status === '0'
  try { await ElMessageBox.confirm(stopped ? `停用“${row.clientName}”并撤销其全部旧授权？` : `恢复“${row.clientName}”？用户仍需重新授权。`, '客户端状态', { type: 'warning', confirmButtonText: stopped ? '停用并撤销' : '恢复', cancelButtonText: '取消' }) }
  catch { return }
  changing.value = true
  try { await setMcpClientStatus(row.id, stopped ? '1' : '0'); ElMessage.success('状态已更新'); await load() }
  catch { /* 请求层已显示后台错误。 */ }
  finally { changing.value = false }
}
void load()
void loadConfiguration()
</script>
