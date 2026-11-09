<template>
  <main class="mcp-page"><section class="mcp-card narrow" aria-labelledby="consent-title">
    <h1 id="consent-title">授权 AI 客户端</h1>
    <el-alert v-if="error" :title="error" type="error" :closable="false" class="mcp-error" role="alert" />
    <template v-if="context">
      <p><strong>{{ context.clientName }}</strong> 请求使用你的账号连接 MCP。</p>
      <dl class="mcp-detail"><dt>客户端标识</dt><dd>{{ context.clientId }}</dd><dt>返回地址</dt><dd>{{ context.redirectUri }}</dd></dl>
      <p>请确认这是你刚刚发起连接的客户端。实际操作始终受你当前的业务权限限制。</p>
      <el-checkbox-group v-model="selected" aria-label="授权范围">
        <p v-for="scope in context.scopes" :key="scope"><el-checkbox :value="scope">{{ scopeLabel(scope) }}</el-checkbox></p>
      </el-checkbox-group>
      <p>允许离线访问后，客户端可以在你关闭此页面后继续连接；你可以随时在“我的连接”中撤销。</p>
      <div class="mcp-actions">
        <el-button type="primary" :loading="busy" :disabled="!selected.includes('mcp:connect')" @click="submit(true)">同意授权</el-button>
        <el-button :disabled="busy" @click="submit(false)">拒绝</el-button>
      </div>
    </template>
    <router-link to="/mcp/connections">我的连接</router-link>
    <p v-if="!context && !error" role="status">正在读取授权请求…</p>
  </section></main>
</template>
<script setup lang="ts" name="McpConsent">
import { ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { createMcpBrowserClient, mcpErrorMessage, McpBrowserError, type McpBrowserClient } from '@/api/mcp/browser'
import type { McpConsent } from '@/types'
import './style.css'
const route = useRoute(), router = useRouter()
const context = ref<McpConsent>(), selected = ref<string[]>([]), busy = ref(false), error = ref('')
let client: McpBrowserClient | undefined
/** 已知范围使用中文含义，未知范围保留明确标识。 */
function scopeLabel(scope: string): string { return scope === 'mcp:connect' ? '连接 MCP 并使用已开放的工具' : scope === 'offline_access' ? '离线访问（允许刷新连接）' : scope }
/** 同意只提交勾选范围；拒绝提交空范围，由服务端终结原请求。 */
async function submit(accepted: boolean): Promise<void> {
  if (!client || !context.value || busy.value) return
  busy.value = true; error.value = ''
  try { await client.submitConsent(context.value, accepted ? selected.value : []) }
  catch (cause) { error.value = mcpErrorMessage(cause); busy.value = false }
}
/** state 仅用于服务端定位，不采用查询串提供的名称、回调或范围。 */
async function initialize(): Promise<void> {
  context.value = undefined; selected.value = []; error.value = ''
  const state = route.query.state
  if (typeof state !== 'string' || !state || state.length > 8192) { error.value = '授权请求无效，请从 AI 客户端重新连接'; return }
  try {
    client = (await createMcpBrowserClient()).client
    const verified = await client.consent(state)
    if (route.query.state !== state) return
    context.value = verified
    selected.value = context.value.scopes.filter(scope => scope === 'mcp:connect')
  } catch (cause) {
    if (route.query.state !== state) return
    // 不把 state 或任意 returnUrl 复制到登录跳转；会话过期后需要重新发起连接。
    if (cause instanceof McpBrowserError && cause.status === 401) { await router.replace('/mcp/login'); return }
    error.value = '授权请求已失效或暂时不可用，请从 AI 客户端重新连接'
  }
}
watch(() => route.query.state, initialize, { immediate: true })
</script>
