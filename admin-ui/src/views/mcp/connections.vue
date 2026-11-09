<template>
  <main class="mcp-page"><section class="mcp-card" aria-labelledby="connections-title">
    <h1 id="connections-title">我的 MCP 连接</h1>
    <div class="mcp-actions"><span v-if="username">当前账号：{{ username }}</span><el-button :disabled="busy" @click="load">刷新</el-button><el-button :disabled="busy || !client" @click="logout">退出 MCP 登录</el-button></div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" class="mcp-error" role="alert" />
    <el-alert v-if="disabled" title="MCP 暂停接受调用，仍可查看和撤销已有连接。" type="warning" :closable="false" />
    <p>撤销后，该客户端需要重新获得你的授权才能继续操作。</p>
    <el-table :data="connections" v-loading="busy" empty-text="暂无连接" row-key="grantId">
      <el-table-column prop="clientName" label="客户端" min-width="160" />
      <el-table-column label="状态" width="120"><template #default="{ row }">{{ row.status === '0' && row.clientStatus === '0' ? '已授权' : '已撤销或停用' }}</template></el-table-column>
      <el-table-column label="授权范围" min-width="180"><template #default="{ row }">{{ row.scopes.join('、') || '无' }}</template></el-table-column>
      <el-table-column label="最近调用" min-width="180"><template #default="{ row }">{{ formatTime(row.lastUsedAt) }}</template></el-table-column>
      <el-table-column label="操作" width="100"><template #default="{ row }"><el-button type="danger" link :disabled="busy || row.status !== '0'" @click="revoke(row)">撤销</el-button></template></el-table-column>
    </el-table>
    <el-pagination v-if="connectionTotal > 20" v-model:current-page="connectionPage" :total="connectionTotal" :page-size="20" layout="prev, pager, next" @current-change="load" />
    <h2>调用记录</h2>
    <el-table :data="calls" empty-text="暂无调用记录" row-key="logId">
      <el-table-column label="时间" min-width="180"><template #default="{ row }">{{ formatTime(row.createdAt) }}</template></el-table-column>
      <el-table-column prop="eventType" label="事件" min-width="140" />
      <el-table-column prop="toolName" label="工具" min-width="180" />
      <el-table-column prop="resultCode" label="结果" min-width="140" />
      <el-table-column prop="elapsedMs" label="耗时（毫秒）" width="120" />
    </el-table>
    <el-pagination v-if="callTotal > 20" v-model:current-page="callPage" :total="callTotal" :page-size="20" layout="prev, pager, next" @current-change="load" />
  </section></main>
</template>
<script setup lang="ts" name="McpConnections">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { createMcpBrowserClient, mcpErrorMessage, McpBrowserError, type McpBrowserClient } from '@/api/mcp/browser'
import type { McpConnection, McpCall } from '@/types'
import './style.css'
const router = useRouter()
const connections = ref<McpConnection[]>([]), calls = ref<McpCall[]>([])
const connectionPage = ref(1), callPage = ref(1), connectionTotal = ref(0), callTotal = ref(0)
const username = ref(''), error = ref(''), busy = ref(false), disabled = ref(false)
let client: McpBrowserClient | undefined
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
