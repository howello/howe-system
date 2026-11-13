<template>
  <main class="mcp-page">
    <section class="mcp-card" aria-labelledby="consent-title">
      <McpBrand badge="授权确认" />

      <h1 id="consent-title">授权 AI 客户端</h1>
      <p class="mcp-intro">确认下方客户端确实是你刚刚发起连接的那个，再选择允许它使用的能力。</p>

      <el-alert v-if="error" :title="error" type="error" :closable="false" class="mcp-error" role="alert" show-icon />

      <template v-if="context">
        <div class="mcp-client-box">
          <span class="mcp-client-box__icon"><el-icon><Connection /></el-icon></span>
          <div>
            <div class="mcp-client-box__name">{{ context.clientName }}</div>
            <div class="mcp-client-box__desc">请求使用你的系统账号连接 MCP</div>
          </div>
        </div>

        <dl class="mcp-detail">
          <div class="mcp-detail__row">
            <dt>客户端标识</dt>
            <dd><code>{{ context.clientId }}</code></dd>
          </div>
          <div class="mcp-detail__row">
            <dt>返回地址</dt>
            <dd><code>{{ context.redirectUri }}</code></dd>
          </div>
        </dl>

        <h2>请求的访问范围</h2>
        <el-checkbox-group v-model="selected" class="mcp-scope-list" aria-label="授权范围">
          <div
            v-for="scope in context.scopes"
            :key="scope"
            class="mcp-scope"
            :class="{ 'is-checked': selected.includes(scope) }"
          >
            <el-checkbox :value="scope">
              {{ scopeLabel(scope) }}
              <span v-if="isRequired(scope)" class="mcp-required">必需</span>
            </el-checkbox>
            <p class="mcp-scope__desc">{{ scopeDesc(scope) }}</p>
          </div>
        </el-checkbox-group>

        <div class="mcp-notice">
          <strong>权限边界：</strong>授权后，客户端仍只能调用你当前账号有权使用的工具；实际操作始终受你的业务权限限制。允许离线访问后，客户端可以在你关闭此页面后继续连接，你可以随时在「我的连接」中撤销。
        </div>

        <div class="mcp-actions mcp-actions--split">
          <el-button size="large" :disabled="busy" @click="submit(false)">拒绝</el-button>
          <el-button
            size="large"
            type="primary"
            :loading="busy"
            :disabled="!selected.includes('mcp:connect')"
            @click="submit(true)"
          >同意授权</el-button>
        </div>
      </template>

      <p v-else-if="!error" class="mcp-loading" role="status">正在读取授权请求…</p>

      <router-link class="mcp-back" to="/mcp/connections">
        <el-icon><ArrowLeft /></el-icon> 查看我的连接
      </router-link>
    </section>
    <p class="mcp-page__foot">授权请求由服务端校验并只属于当前登录账号。</p>
  </main>
</template>

<script setup lang="ts" name="McpConsent">
import { ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, Connection } from '@element-plus/icons-vue'
import { createMcpBrowserClient, mcpErrorMessage, McpBrowserError, type McpBrowserClient } from '@/api/mcp/browser'
import type { McpConsent } from '@/types'
import McpBrand from './components/McpBrand.vue'
import { mcpDefaultSelected, mcpScopeDescription, mcpScopeLabel, mcpScopeRequired } from '@/utils/mcpScopes'
import './style.css'

const route = useRoute(), router = useRouter()
const context = ref<McpConsent>(), selected = ref<string[]>([]), busy = ref(false), error = ref('')
let client: McpBrowserClient | undefined
/** 已知范围使用中文含义，未知范围保留明确标识。 */
function scopeLabel(scope: string): string { return mcpScopeLabel(scope) }
/** 每个范围的用途说明，帮助用户判断是否同意。 */
function scopeDesc(scope: string): string { return mcpScopeDescription(scope) }
/** 必需范围由服务端声明决定，不在页面重复硬编码。 */
function isRequired(scope: string): boolean { return mcpScopeRequired(scope) }
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
    selected.value = mcpDefaultSelected(context.value.scopes)
  } catch (cause) {
    if (route.query.state !== state) return
    // 不把 state 或任意 returnUrl 复制到登录跳转；会话过期后需要重新发起连接。
    if (cause instanceof McpBrowserError && cause.status === 401) { await router.replace('/mcp/login'); return }
    error.value = '授权请求已失效或暂时不可用，请从 AI 客户端重新连接'
  }
}
watch(() => route.query.state, initialize, { immediate: true })
</script>
