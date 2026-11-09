<template>
  <div class="app-container">
    <el-alert title="目录来自实际注册的工具代码。此处只能控制开放状态；首期仅提供连接检查。" type="info" :closable="false" class="mb8" />
    <el-button @click="load" :loading="loading" class="mb8">刷新目录</el-button>
    <el-table :data="rows" v-loading="loading" row-key="schema.name" empty-text="暂无工具">
      <el-table-column prop="schema.name" label="工具名称" min-width="230" />
      <el-table-column prop="schema.title" label="标题" min-width="160" />
      <el-table-column prop="version" label="版本" width="80" />
      <el-table-column prop="scope" label="授权范围" min-width="130" />
      <el-table-column label="开放状态" width="100"><template #default="{ row }"><el-tag :type="row.enabled === 'Y' ? 'success' : 'info'">{{ row.enabled === 'Y' ? '开放' : '关闭' }}</el-tag></template></el-table-column>
      <el-table-column prop="timeoutMs" label="超时（毫秒）" width="130" />
      <el-table-column label="操作" width="170"><template #default="{ row }">
        <el-button link type="primary" v-hasPermi="['mcp:tool:query']" @click="detail(row)">契约详情</el-button>
        <el-button link :type="row.enabled === 'Y' ? 'danger' : 'primary'" :disabled="changing" v-hasPermi="['mcp:tool:edit']" @click="changeStatus(row)">{{ row.enabled === 'Y' ? '关闭' : '开放' }}</el-button>
      </template></el-table-column>
    </el-table>
    <AdminDetail v-model="open" title="工具契约" :fields="fields" />
  </div>
</template>
<script setup lang="ts" name="McpTool">
import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listMcpTools, getMcpTool, setMcpToolStatus } from '@/api/mcp/management'
import type { McpAdminTool } from '@/types'
import AdminDetail from '../components/AdminDetail.vue'
const rows = ref<McpAdminTool[]>([]), loading = ref(false), changing = ref(false), open = ref(false)
const fields = ref<Record<string, unknown>>({})
/** 读取代码目录及实时状态。 */
async function load(): Promise<void> {
  loading.value = true
  try { rows.value = (await listMcpTools()).rows }
  catch { rows.value = [] }
  finally { loading.value = false }
}
/** 工具参数与权限映射仅作为只读契约展示。 */
async function detail(row: McpAdminTool): Promise<void> {
  try {
    const tool = (await getMcpTool(row.schema.name)).data!
    fields.value = { '名称': tool.schema.name, '描述': tool.schema.description, '版本': tool.version, '授权范围': tool.scope,
      '业务权限': tool.permissions, '输入约束': tool.schema.inputSchema, '行为注解': tool.schema.annotations,
      '超时（毫秒）': tool.timeoutMs, '最大结果（字节）': tool.maxResultBytes, '更新者': tool.updateBy, '更新时间': tool.updatedAt }
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
  try { await setMcpToolStatus(row.schema.name, enabled ? 'Y' : 'N'); ElMessage.success('工具状态已更新'); await load() }
  catch { /* 请求层已显示后台错误。 */ }
  finally { changing.value = false }
}
void load()
</script>
