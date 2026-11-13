<template>
  <div class="app-container">
    <el-form :model="query" :inline="true" v-show="showSearch" label-width="80px">
      <el-form-item label="工具" prop="keyword">
        <el-input
          v-model="query.keyword"
          placeholder="名称或标题"
          clearable
          maxlength="128"
          style="width: 200px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="所属模块" prop="module">
        <el-select v-model="query.module" placeholder="全部模块" clearable style="width: 160px" @change="handleModuleChange">
          <el-option v-for="item in moduleOptions" :key="item" :label="moduleLabel(item)" :value="item" />
        </el-select>
      </el-form-item>
      <el-form-item label="能力分组" prop="capabilityGroup">
        <el-select v-model="query.capabilityGroup" placeholder="全部分组" clearable filterable style="width: 220px">
          <el-option v-for="item in groupOptions" :key="item" :label="item" :value="item" />
        </el-select>
      </el-form-item>
      <el-form-item label="开放状态" prop="enabled">
        <el-select v-model="query.enabled" placeholder="全部状态" clearable style="width: 140px">
          <el-option label="开放" value="Y" />
          <el-option label="关闭" value="N" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button
          type="primary"
          plain
          icon="CircleCheck"
          :loading="changing"
          @click="changeAll('Y')"
          v-hasPermi="['mcp:tool:edit']"
        >全部开放</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="danger"
          plain
          icon="CircleClose"
          :loading="changing"
          @click="changeAll('N')"
          v-hasPermi="['mcp:tool:edit']"
        >全部关闭</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button plain icon="Refresh" :loading="loading" @click="getList">刷新目录</el-button>
      </el-col>
      <el-tooltip content="批量操作固定作用于代码目录全部工具，不受查询条件和分页影响" placement="top">
        <el-icon class="mcp-tool-tip"><InfoFilled /></el-icon>
      </el-tooltip>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-alert
      v-if="summary"
      :title="summaryTitle"
      :type="summary.enabledTotal === summary.catalogTotal ? 'success' : 'info'"
      :closable="false"
      show-icon
      class="mb8"
    />

    <el-table v-loading="loading" :data="rows" row-key="schema.name" empty-text="暂无工具">
      <el-table-column label="工具" min-width="260">
        <template #default="{ row }">
          <div class="mcp-tool-name">
            <el-icon class="mcp-tool-name__icon"><Connection /></el-icon>
            <div>
              <div class="mcp-mono">{{ row.schema.name }}</div>
              <div class="mcp-muted">{{ row.schema.title || '未提供标题' }}</div>
            </div>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="模块" width="90">
        <template #default="{ row }"><el-tag effect="plain" size="small">{{ moduleLabel(mcpModuleOf(row.capabilityGroup)) }}</el-tag></template>
      </el-table-column>
      <el-table-column prop="capabilityGroup" label="能力分组" min-width="160" show-overflow-tooltip />
      <el-table-column prop="version" label="版本" width="80" align="center" />
      <el-table-column label="授权范围" min-width="150">
        <template #default="{ row }"><el-tag effect="plain" size="small">{{ row.scope }}</el-tag></template>
      </el-table-column>
      <el-table-column label="开放状态" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="row.enabled === 'Y' ? 'success' : 'info'" effect="light" round>{{ row.enabled === 'Y' ? '开放' : '关闭' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="超时" width="100" align="right">
        <template #default="{ row }"><span class="mcp-mono">{{ row.timeoutMs }} ms</span></template>
      </el-table-column>
      <el-table-column label="操作" width="170" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" :icon="Document" v-hasPermi="['mcp:tool:query']" @click="detail(row)">契约详情</el-button>
          <el-button
            link
            :type="row.enabled === 'Y' ? 'danger' : 'primary'"
            :icon="row.enabled === 'Y' ? CircleClose : CircleCheck"
            :disabled="changing"
            v-hasPermi="['mcp:tool:edit']"
            @click="changeStatus(row)"
          >{{ row.enabled === 'Y' ? '关闭' : '开放' }}</el-button>
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

    <el-dialog v-model="open" title="工具契约" width="min(780px, 94vw)" append-to-body>
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

<script setup lang="ts" name="McpTool">
import { computed, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { CircleCheck, CircleClose, Connection, Document, InfoFilled } from '@element-plus/icons-vue'
import { listMcpTools, getMcpTool, setMcpToolStatus, setAllMcpToolStatus, getMcpToolSummary } from '@/api/mcp/management'
import type { McpAdminTool, McpAdminToolQuery, McpAdminToolSummary } from '@/types'
import { mcpModuleLabel, mcpModuleOf, mcpSortModules } from '@/utils/mcpModules'

const showSearch = ref(true)
const query = reactive<McpAdminToolQuery>({ pageNum: 1, pageSize: 20 })
const rows = ref<McpAdminTool[]>([]), total = ref(0), loading = ref(false), changing = ref(false), open = ref(false)
const summary = ref<McpAdminToolSummary>()
const fields = ref<Record<string, unknown>>({})

/** 模块筛选项来自全量统计，避免分页结果缺模块。 */
const moduleOptions = computed(() => mcpSortModules(summary.value?.modules ?? []))
/** 能力分组随模块收窄，避免选择互相冲突的条件。 */
const groupOptions = computed(() => {
  const groups = summary.value?.capabilityGroups ?? []
  return query.module ? groups.filter(group => mcpModuleOf(group) === query.module) : groups
})
/** 顶部展示全量开放数量，不把当前页 rows 当作全量。 */
const summaryTitle = computed(() => summary.value
  ? `共 ${summary.value.catalogTotal} 个工具，开放 ${summary.value.enabledTotal} 个；当前查询匹配 ${total.value} 个`
  : '正在读取工具目录统计…')

/** 已知模块使用中文名称。 */
function moduleLabel(id: string): string { return mcpModuleLabel(id) }
/** 只按需读取全量统计与筛选选项。 */
async function loadSummary(): Promise<void> {
  try { summary.value = (await getMcpToolSummary()).data }
  catch { summary.value = undefined }
}
/** 分页查询；失败时清空列表，避免留下上次查询的误导数据。 */
async function getList(): Promise<void> {
  loading.value = true
  try {
    const result = await listMcpTools({ ...query })
    rows.value = result.rows
    total.value = result.total
  } catch { rows.value = []; total.value = 0 }
  finally { loading.value = false }
}
/** 查询条件变化从第一页开始。 */
function handleQuery(): void { query.pageNum = 1; void getList() }
/** 模块变化时清空不再兼容的能力分组。 */
function handleModuleChange(): void { query.capabilityGroup = undefined }
/** 清空全部查询条件。 */
function resetQuery(): void {
  query.keyword = undefined; query.module = undefined; query.capabilityGroup = undefined; query.enabled = undefined
  handleQuery()
}
/** 工具参数与权限映射仅作为只读契约展示。 */
async function detail(row: McpAdminTool): Promise<void> {
  try {
    const tool = (await getMcpTool(row.schema.name)).data!
    fields.value = { '名称': tool.schema.name, '描述': tool.schema.description, '模块': moduleLabel(mcpModuleOf(tool.capabilityGroup)),
      '能力分组': tool.capabilityGroup, '版本': tool.version, '授权范围': tool.scope, '业务权限': tool.permissions,
      '输入约束': tool.schema.inputSchema, '行为注解': tool.schema.annotations, '超时（毫秒）': tool.timeoutMs,
      '最大结果（字节）': tool.maxResultBytes, '更新者': tool.updateBy, '更新时间': tool.updatedAt }
    open.value = true
  } catch { /* 请求层已显示后台错误。 */ }
}
/** 切换工具状态不提供动态执行代码的入口。 */
async function changeStatus(row: McpAdminTool): Promise<void> {
  if (changing.value) return
  const enabled = row.enabled !== 'Y'
  try { await ElMessageBox.confirm(`${enabled ? '开放' : '关闭'}工具“${row.schema.name}”？`, '工具状态', { type: 'warning', cancelButtonText: '取消', confirmButtonText: '确认' }) }
  catch { return }
  changing.value = true
  try { await setMcpToolStatus(row.schema.name, enabled ? 'Y' : 'N'); ElMessage.success('工具状态已更新'); await reload() }
  catch { /* 请求层已显示后台错误。 */ }
  finally { changing.value = false }
}

/** 关闭后客户端会收到「工具不可用」，运行中的操作仍需按「我的操作」核对。 */
async function changeAll(enabled: 'Y' | 'N'): Promise<void> {
  if (changing.value) return
  const action = enabled === 'Y' ? '开放' : '关闭'
  const scopeHint = enabled === 'N'
    ? '关闭只影响后续可用性判断，不会撤销授权，也不会中断已排队或执行中的操作。'
    : '开放后仍需有效授权、scope 与业务权限，写操作仍逐次确认。'
  try {
    await ElMessageBox.confirm(
      `将${action}代码目录中的全部工具（不受当前查询条件和分页影响），共 ${summary.value?.catalogTotal ?? '全部'} 个。${scopeHint}`,
      `全部${action}`,
      { type: 'warning', confirmButtonText: `全部${action}`, cancelButtonText: '取消' }
    )
  } catch { return }
  changing.value = true
  try {
    const result = (await setAllMcpToolStatus(enabled)).data
    ElMessage.success(`已${action} ${result?.changed ?? 0} 个工具${result?.unchanged ? `，${result.unchanged} 个原本已是该状态` : ''}`)
    await reload()
  } catch { /* 请求层已显示后台错误。 */ }
  finally { changing.value = false }
}
/** 状态变化后同时刷新列表与全量统计。 */
async function reload(): Promise<void> {
  await Promise.all([getList(), loadSummary()])
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
void loadSummary()
</script>

<style scoped>
.mcp-tool-name {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}
.mcp-tool-name__icon {
  flex: none;
  width: 30px;
  height: 30px;
  border-radius: 9px;
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
}
.mcp-tool-tip {
  align-self: center;
  color: var(--el-text-color-secondary);
}
.mcp-mono {
  font-family: ui-monospace, SFMono-Regular, Consolas, 'Liberation Mono', monospace;
  font-size: 12px;
}
.mcp-muted {
  color: var(--el-text-color-secondary);
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
