<template>
  <div class="app-container mcp-admin">
    <McpPageHeader
      title="工具目录"
      eyebrow="实际注册的工具契约"
      description="目录来自实际注册的工具代码，此处只能控制开放状态；首期仅提供连接检查。"
    >
      <template #icon><el-icon><Tools /></el-icon></template>
      <template #actions>
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新目录</el-button>
      </template>
    </McpPageHeader>

    <section class="mcp-panel mcp-table">
      <div class="mcp-panel__head">
        <div>
          <h2 class="mcp-panel__title">工具清单</h2>
          <p class="mcp-panel__desc">共 {{ rows.length }} 个工具；关闭后客户端会收到「工具不可用」，不会执行任何业务逻辑。</p>
        </div>
        <div class="mcp-panel__toolbar">
          <span class="mcp-chip">开放 {{ enabledCount }} / {{ rows.length }}</span>
        </div>
      </div>
      <div class="mcp-panel__body mcp-panel__body--flush">
        <el-table :data="rows" v-loading="loading" row-key="schema.name" empty-text="暂无工具">
          <el-table-column label="工具" min-width="260">
            <template #default="{ row }">
              <div class="mcp-tool-name">
                <el-icon class="mcp-tool-name__icon"><Connection /></el-icon>
                <div>
                  <div class="mcp-mono">{{ row.schema.name }}</div>
                  <div class="mcp-muted mcp-tool-title">{{ row.schema.title || '未提供标题' }}</div>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="version" label="版本" width="80" align="center" />
          <el-table-column label="授权范围" min-width="140">
            <template #default="{ row }"><span class="mcp-chip">{{ row.scope }}</span></template>
          </el-table-column>
          <el-table-column label="开放状态" width="100">
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
      </div>
    </section>

    <AdminDetail v-model="open" title="工具契约" :fields="fields" />
  </div>
</template>

<script setup lang="ts" name="McpTool">
import { ref, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { CircleCheck, CircleClose, Connection, Document, Refresh, Tools } from '@element-plus/icons-vue'
import { listMcpTools, getMcpTool, setMcpToolStatus } from '@/api/mcp/management'
import type { McpAdminTool } from '@/types'
import AdminDetail from '../components/AdminDetail.vue'
import McpPageHeader from '../components/McpPageHeader.vue'
import '../admin.css'

const rows = ref<McpAdminTool[]>([]), loading = ref(false), changing = ref(false), open = ref(false)
const fields = ref<Record<string, unknown>>({})
/** 顶部展示当前开放数量，便于一眼确认总开关状态。 */
const enabledCount = computed(() => rows.value.filter((row: McpAdminTool) => row.enabled === 'Y').length)
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
.mcp-tool-title {
  margin-top: 2px;
  font-size: 12px;
}
</style>
