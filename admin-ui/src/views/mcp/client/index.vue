<template>
  <div class="app-container mcp-admin">
    <McpPageHeader
      title="客户端"
      eyebrow="外部客户端登记与运行状态"
      description="AI 客户端经 DCR 或 CIMD 自动登记后显示在此。停用会撤销旧授权，恢复后用户需重新授权。"
    >
      <template #icon><el-icon><Connection /></el-icon></template>
      <template #actions>
        <el-button :icon="Refresh" :loading="configurationLoading" @click="loadConfiguration">刷新运行配置</el-button>
        <el-button :icon="Link" @click="openConnections">我的 MCP 连接</el-button>
      </template>
    </McpPageHeader>

    <section class="mcp-panel">
      <div class="mcp-panel__head">
        <div>
          <h2 class="mcp-panel__title">运行配置</h2>
          <p class="mcp-panel__desc">地址在启动时冻结；在线修改后需重启后端，当前连接仍使用已生效地址。</p>
        </div>
        <div class="mcp-panel__toolbar">
          <span class="mcp-status" :class="{ 'mcp-status--off': configuration && !configuration.enabled }">
            {{ configuration ? (configuration.enabled ? '运行开关已开启' : '运行开关已关闭') : '读取中' }}
          </span>
        </div>
      </div>
      <div class="mcp-panel__body">
        <el-alert
          v-if="configurationError"
          title="运行配置读取失败，无法确认地址是否已生效。"
          type="error"
          :closable="false"
          show-icon
        />
        <template v-else-if="!configuration">
          <div class="mcp-loading">正在读取运行配置…</div>
        </template>
        <template v-else>
          <el-alert
            v-if="!configuration.configuredValid"
            title="在线地址配置无效，请在参数设置中修正后再重启；当前仍使用下方已生效地址。"
            type="error"
            :closable="false"
            show-icon
            class="mb10"
          />
          <el-alert
            v-else-if="configuration.restartRequired"
            title="在线地址已修改，需重启后端生效。当前连接继续使用下方运行地址。"
            type="warning"
            :closable="false"
            show-icon
            class="mb10"
          />
          <div class="mcp-config">
            <div class="mcp-meta mcp-meta--wide">
              <div class="mcp-meta__label"><el-icon><Link /></el-icon>当前 MCP 连接地址</div>
              <div class="mcp-meta__value"><code>{{ configuration.active.resource }}</code></div>
            </div>
            <div class="mcp-meta">
              <div class="mcp-meta__label"><el-icon><Key /></el-icon>当前 OAuth issuer</div>
              <div class="mcp-meta__value"><code>{{ configuration.active.issuer }}</code></div>
            </div>
            <div class="mcp-meta">
              <div class="mcp-meta__label"><el-icon><Monitor /></el-icon>当前授权页面站点</div>
              <div class="mcp-meta__value"><code>{{ configuration.active.frontendBaseUrl }}</code></div>
            </div>
          </div>
          <div v-if="configuration.restartRequired && configuration.configured" class="mcp-pending">
            <p class="mcp-pending__title">已保存、等待重启后生效</p>
            <div class="mcp-pending__line"><span>issuer</span><code class="mcp-mono">{{ configuration.configured.issuer }}</code></div>
            <div class="mcp-pending__line"><span>前端站点</span><code class="mcp-mono">{{ configuration.configured.frontendBaseUrl }}</code></div>
          </div>
        </template>
      </div>
    </section>

    <section class="mcp-filter">
      <el-form :inline="true" :model="query" @submit.prevent="search">
        <el-form-item label="客户端">
          <el-input v-model="query.keyword" placeholder="名称或客户端标识" clearable maxlength="128" @keyup.enter="search" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 140px">
            <el-option label="正常" value="0" />
            <el-option label="停用" value="1" />
          </el-select>
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
          <h2 class="mcp-panel__title">已登记客户端</h2>
          <p class="mcp-panel__desc">共 {{ total }} 个客户端；回调地址与认证方式在详情中只读查看。</p>
        </div>
      </div>
      <div class="mcp-panel__body mcp-panel__body--flush">
        <el-table :data="rows" v-loading="loading" row-key="id" empty-text="暂无客户端">
          <el-table-column prop="clientName" label="名称" min-width="160" show-overflow-tooltip />
          <el-table-column prop="clientId" label="客户端标识" min-width="220" show-overflow-tooltip />
          <el-table-column label="登记方式" width="100">
            <template #default="{ row }"><span class="mcp-chip">{{ row.registrationSource }}</span></template>
          </el-table-column>
          <el-table-column label="状态" width="96">
            <template #default="{ row }">
              <el-tag :type="row.status === '0' ? 'success' : 'info'" effect="light" round>
                {{ row.status === '0' ? '正常' : '停用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="元数据" width="110">
            <template #default="{ row }">
              <template v-if="row.registrationSource !== 'CIMD'"><span class="mcp-muted">不适用</span></template>
              <el-tag v-else :type="row.metadataValid === 'Y' ? 'success' : 'danger'" effect="plain" size="small">
                {{ row.metadataValid === 'Y' ? '有效' : '验证失败' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="grantCount" label="有效授权" width="96" align="center" />
          <el-table-column prop="createdAt" label="登记时间" min-width="180" />
          <el-table-column label="操作" fixed="right" width="160">
            <template #default="{ row }">
              <el-button link type="primary" :icon="View" v-hasPermi="['mcp:client:query']" @click="detail(row)">详情</el-button>
              <el-button
                link
                :type="row.status === '0' ? 'danger' : 'primary'"
                :icon="row.status === '0' ? CircleClose : CircleCheck"
                :disabled="changing"
                v-hasPermi="['mcp:client:edit']"
                @click="changeStatus(row)"
              >{{ row.status === '0' ? '停用' : '恢复' }}</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
      <div v-if="total > 0" class="mcp-panel__foot">
        <pagination :total="total" v-model:page="query.pageNum" v-model:limit="query.pageSize" @pagination="load" />
      </div>
    </section>

    <AdminDetail v-model="open" title="客户端公开详情" :fields="fields" />
  </div>
</template>

<script setup lang="ts" name="McpClient">
import { ref, reactive } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { CircleCheck, CircleClose, Connection, Key, Link, Monitor, Refresh, Search, View } from '@element-plus/icons-vue'
import { listMcpClients, getMcpClient, setMcpClientStatus, getMcpConfiguration } from '@/api/mcp/management'
import type { McpAdminClient, McpAdminQuery, McpAdminConfiguration } from '@/types'
import AdminDetail from '../components/AdminDetail.vue'
import McpPageHeader from '../components/McpPageHeader.vue'
import '../admin.css'

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
/** 仪表授权的独立登录页不依赖后台 JWT，直接在新标签打开。 */
function openConnections(): void {
  window.open('/mcp/connections', '_blank', 'noopener')
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
