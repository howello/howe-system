<template>
  <div class="app-container">
    <el-card shadow="never" class="mb8">
      <template #header>
        <div class="mcp-card-head">
          <span>运行配置</span>
          <el-tag :type="configuration && configuration.enabled ? 'success' : 'info'" effect="light">
            {{ configuration ? (configuration.enabled ? '运行开关已开启' : '运行开关已关闭') : '读取中' }}
          </el-tag>
        </div>
      </template>
      <el-alert
        v-if="configurationError"
        title="运行配置读取失败，无法确认地址是否已生效。"
        type="error"
        :closable="false"
        show-icon
      />
      <div v-else-if="!configuration" class="mcp-loading">正在读取运行配置…</div>
      <template v-else>
        <el-alert
          v-if="!configuration.configuredValid"
          title="在线地址配置无效，请在参数设置中修正后再重启；当前仍使用下方已生效地址。"
          type="error"
          :closable="false"
          show-icon
          class="mb8"
        />
        <el-alert
          v-else-if="configuration.restartRequired"
          title="在线地址已修改，需重启后端生效。当前连接继续使用下方运行地址。"
          type="warning"
          :closable="false"
          show-icon
          class="mb8"
        />
        <el-descriptions :column="2" border>
          <el-descriptions-item label="当前 MCP 连接地址" :span="2"><code>{{ configuration.active.resource }}</code></el-descriptions-item>
          <el-descriptions-item label="当前 OAuth issuer" :span="2"><code>{{ configuration.active.issuer }}</code></el-descriptions-item>
          <el-descriptions-item label="当前授权页面站点" :span="2"><code>{{ configuration.active.frontendBaseUrl }}</code></el-descriptions-item>
        </el-descriptions>
        <div v-if="configuration.restartRequired && configuration.configured" class="mcp-pending">
          <p class="mcp-pending__title">已保存、等待重启后生效</p>
          <div class="mcp-pending__line"><span>issuer</span><code>{{ configuration.configured.issuer }}</code></div>
          <div class="mcp-pending__line"><span>前端站点</span><code>{{ configuration.configured.frontendBaseUrl }}</code></div>
        </div>
      </template>
    </el-card>

    <el-form :model="query" :inline="true" v-show="showSearch" label-width="80px">
      <el-form-item label="客户端" prop="keyword">
        <el-input v-model="query.keyword" placeholder="名称或客户端标识" clearable maxlength="128" style="width: 220px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 160px">
          <el-option label="正常" value="0" />
          <el-option label="停用" value="1" />
        </el-select>
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
        <el-button plain icon="Link" @click="openConnections">我的 MCP 连接</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="rows" row-key="id" empty-text="暂无客户端">
      <el-table-column prop="clientName" label="名称" min-width="160" show-overflow-tooltip />
      <el-table-column prop="clientId" label="客户端标识" min-width="220" show-overflow-tooltip />
      <el-table-column label="登记方式" width="100" align="center">
        <template #default="{ row }"><el-tag effect="plain" size="small">{{ row.registrationSource }}</el-tag></template>
      </el-table-column>
      <el-table-column label="状态" width="96" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === '0' ? 'success' : 'info'" effect="light" round>
            {{ row.status === '0' ? '正常' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="元数据" width="110" align="center">
        <template #default="{ row }">
          <span v-if="row.registrationSource !== 'CIMD'" class="mcp-muted">不适用</span>
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

    <pagination
      v-show="total > 0"
      :total="total"
      v-model:page="query.pageNum"
      v-model:limit="query.pageSize"
      @pagination="getList"
    />

    <el-dialog v-model="open" title="客户端公开详情" width="min(780px, 94vw)" append-to-body>
      <el-descriptions :column="1" border>
        <el-descriptions-item v-for="(value, label) in fields" :key="label" :label="label">
          <pre v-if="isBlock(value)" class="mcp-code">{{ display(value) }}</pre>
          <span v-else>{{ display(value) }}</span>
        </el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="open = false">关 闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="McpClient">
import { reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { CircleCheck, CircleClose, Link, View } from '@element-plus/icons-vue'
import { listMcpClients, getMcpClient, setMcpClientStatus, getMcpConfiguration } from '@/api/mcp/management'
import type { McpAdminClient, McpAdminQuery, McpAdminConfiguration } from '@/types'

const showSearch = ref(true)
const query = reactive<McpAdminQuery>({ pageNum: 1, pageSize: 20 })
const rows = ref<McpAdminClient[]>([]), total = ref(0), loading = ref(false), changing = ref(false), open = ref(false)
const fields = ref<Record<string, unknown>>({})
const configuration = ref<McpAdminConfiguration>()
const configurationLoading = ref(false), configurationError = ref(false)

/** 运行状态独立刷新；失败时清空旧快照，不把旧数据当成当前配置。 */
async function loadConfiguration(): Promise<void> {
  if (configurationLoading.value) return
  configurationLoading.value = true; configurationError.value = false
  try { configuration.value = (await getMcpConfiguration()).data }
  catch { configuration.value = undefined; configurationError.value = true }
  finally { configurationLoading.value = false }
}
/** 独立登录页不依赖后台 JWT，直接在新标签打开。 */
function openConnections(): void {
  window.open('/mcp/connections', '_blank', 'noopener')
}
/** 获取后台分页列表，失败时不留下上次查询的误导数据。 */
async function getList(): Promise<void> {
  loading.value = true
  try {
    const result = await listMcpClients({ ...query })
    rows.value = result.rows
    total.value = result.total
  } catch { rows.value = []; total.value = 0 }
  finally { loading.value = false }
}
/** 查询条件变更从第一页开始。 */
function handleQuery(): void { query.pageNum = 1; void getList() }
/** 清空可选筛选条件。 */
function resetQuery(): void { query.keyword = undefined; query.status = undefined; handleQuery() }
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
  try { await setMcpClientStatus(row.id, stopped ? '1' : '0'); ElMessage.success('状态已更新'); await getList() }
  catch { /* 请求层已显示后台错误。 */ }
  finally { changing.value = false }
}
/** 对象、数组或长文本改用代码块展示。 */
function isBlock(value: unknown): boolean {
  return (typeof value === 'object' && value !== null) || (typeof value === 'string' && value.length > 72)
}
/** 白名单详情使用文本插值，不执行任何 HTML。 */
function display(value: unknown): string {
  if (value === null || value === undefined || value === '') return '—'
  return typeof value === 'object' ? JSON.stringify(value, null, 2) : String(value)
}

void getList()
void loadConfiguration()
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
.mcp-muted {
  color: var(--el-text-color-secondary);
}
.mcp-pending {
  margin-top: 12px;
  padding: 12px 14px;
  border: 1px dashed var(--el-color-warning-light-5);
  border-radius: 10px;
  background: var(--el-color-warning-light-9);
}
.mcp-pending__title {
  margin: 0 0 8px;
  color: var(--el-color-warning-dark-2);
  font-size: 12px;
  font-weight: 600;
}
.mcp-pending__line {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 12px;
  margin-top: 6px;
  color: var(--el-text-color-regular);
  font-size: 12px;
}
.mcp-code {
  margin: 0;
  max-height: 300px;
  overflow: auto;
  font-family: ui-monospace, SFMono-Regular, Consolas, 'Liberation Mono', monospace;
  font-size: 12px;
  line-height: 1.65;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
</style>
