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

        <div class="mcp-scope-head">
          <div>
            <h2>请求的访问范围</h2>
            <p class="mcp-scope-head__hint">已选 {{ selected.length }} / {{ context.scopes.length }} 项；默认全部勾选，请取消不需要的范围。</p>
          </div>
          <div class="mcp-scope-head__actions">
            <el-button size="small" :disabled="busy" @click="selectModule">当前模块全选</el-button>
            <el-button size="small" type="primary" plain :disabled="busy" @click="selectAll">全部全选</el-button>
          </div>
        </div>

        <el-tabs v-model="activeModule" class="mcp-scope-tabs" @tab-change="onTabChange">
          <el-tab-pane v-for="group in groups" :key="group.module" :name="group.module">
            <template #label>
              <span class="mcp-tab-label">{{ moduleLabel(group.module) }}<span class="mcp-tab-count">{{ selectedIn(group.scopes) }}/{{ group.scopes.length }}</span></span>
            </template>
            <div class="mcp-scope-grid">
              <div
                v-for="scope in group.scopes"
                :key="scope"
                class="mcp-scope-card"
                :class="{ 'is-checked': selected.includes(scope), 'is-required': isRequired(scope) }"
                @click="toggle(scope, !selected.includes(scope))"
              >
                <el-checkbox
                  :model-value="selected.includes(scope)"
                  :disabled="busy || isRequired(scope)"
                  :aria-label="`${scopeLabel(scope)}，${isRequired(scope) ? '必需，不可取消' : '点击选择'}`"
                  @click.stop
                  @change="toggle(scope, $event)"
                >
                  <span class="mcp-scope-card__title">{{ scopeLabel(scope) }}</span>
                  <span v-if="isRequired(scope)" class="mcp-required">必需</span>
                </el-checkbox>
                <code class="mcp-scope-card__id">{{ scope }}</code>
                <p class="mcp-scope-card__desc">{{ scopeDesc(scope) }}</p>
                <ul v-if="toolsOf(scope).length" class="mcp-scope-card__tools">
                  <li v-for="tool in toolsOf(scope)" :key="tool.name">
                    <span class="mcp-scope-card__tool-name">{{ tool.title || tool.name }}</span>
                    <code v-if="tool.title">{{ tool.name }}</code>
                  </li>
                </ul>
                <p v-else class="mcp-scope-card__empty">{{ emptyToolHint(scope) }}</p>
              </div>
            </div>
          </el-tab-pane>
        </el-tabs>

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
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, Connection } from '@element-plus/icons-vue'
import { createMcpBrowserClient, mcpErrorMessage, McpBrowserError, type McpBrowserClient } from '@/api/mcp/browser'
import type { McpConsent } from '@/types'
import McpBrand from './components/McpBrand.vue'
import { mcpDefaultSelected, mcpGroupScopes, mcpScopeDescription, mcpScopeLabel, mcpScopeRequired } from '@/utils/mcpScopes'
import { mcpModuleLabel } from '@/utils/mcpModules'
import './style.css'

const route = useRoute(), router = useRouter()
const context = ref<McpConsent>(), selected = ref<string[]>([]), busy = ref(false), error = ref('')
const activeModule = ref('')
let client: McpBrowserClient | undefined

/** 本次请求的 scope 按模块分组，顺序固定。 */
const groups = computed(() => mcpGroupScopes(context.value?.scopes ?? []))
/** 已知范围使用中文含义，未知范围保留明确标识。 */
function scopeLabel(scope: string): string { return mcpScopeLabel(scope) }
/** 每个范围的用途说明，帮助用户判断是否同意。 */
function scopeDesc(scope: string): string { return mcpScopeDescription(scope) }
/** 必需范围由服务端声明决定，不在页面重复硬编码。 */
function isRequired(scope: string): boolean { return mcpScopeRequired(scope) }
/** 模块中文名。 */
function moduleLabel(module: string): string { return mcpModuleLabel(module) }
/** 某个范围内已勾选数量。 */
function selectedIn(scopes: string[]): number { return scopes.filter(scope => selected.value.includes(scope)).length }
/** 该范围在代码目录中覆盖的工具；离线访问等范围没有工具。 */
function toolsOf(scope: string): Array<{ name: string; title: string | null }> {
  return context.value?.scopeTools?.find(item => item.scope === scope)?.tools ?? []
}
/** 空工具说明区分离线访问与纯能力范围。 */
function emptyToolHint(scope: string): string {
  return scope === 'offline_access'
    ? '允许刷新令牌，让客户端在页面关闭后保持连接；不额外增加工具。'
    : '该范围当前没有已注册工具，仅保留授权能力。'
}
/** 单个卡片只翻转一次，必需范围保持选中。 */
function toggle(scope: string, checked: boolean | string | number): void {
  if (busy.value || isRequired(scope)) return
  const next = new Set(selected.value)
  if (checked) next.add(scope)
  else next.delete(scope)
  selected.value = context.value?.scopes.filter(item => next.has(item)) ?? Array.from(next)
}
/** 当前 tab 全选只补当前模块，不清空其他模块。 */
function selectModule(): void {
  const group = groups.value.find(item => item.module === activeModule.value)
  if (!group) return
  const next = new Set(selected.value)
  group.scopes.forEach(scope => next.add(scope))
  selected.value = context.value?.scopes.filter(item => next.has(item)) ?? Array.from(next)
}
/** 全局全选恢复本次请求的全部范围，不添加未请求的权限。 */
function selectAll(): void {
  selected.value = mcpDefaultSelected(context.value?.scopes ?? [])
}
/** 切换 tab 只改变展示，选择集合保持不变。 */
function onTabChange(): void { /* 选择状态由页面级 selected 持有。 */ }
/** 同意只提交勾选范围；拒绝提交空范围，由服务端终结原请求。 */
async function submit(accepted: boolean): Promise<void> {
  if (!client || !context.value || busy.value) return
  busy.value = true; error.value = ''
  try { await client.submitConsent(context.value, accepted ? selected.value : []) }
  catch (cause) { error.value = mcpErrorMessage(cause); busy.value = false }
}
/** state 仅用于服务端定位，不采用查询串提供的名称、回调或范围。 */
async function initialize(): Promise<void> {
  context.value = undefined; selected.value = []; error.value = ''; activeModule.value = ''
  const state = route.query.state
  if (typeof state !== 'string' || !state || state.length > 8192) { error.value = '授权请求无效，请从 AI 客户端重新连接'; return }
  try {
    client = (await createMcpBrowserClient()).client
    const verified = await client.consent(state)
    if (route.query.state !== state) return
    context.value = verified
    selected.value = mcpDefaultSelected(verified.scopes)
    activeModule.value = mcpGroupScopes(verified.scopes)[0]?.module ?? ''
  } catch (cause) {
    if (route.query.state !== state) return
    // 不把 state 或任意 returnUrl 复制到登录跳转；会话过期后需要重新发起连接。
    if (cause instanceof McpBrowserError && cause.status === 401) { await router.replace('/mcp/login'); return }
    error.value = '授权请求已失效或暂时不可用，请从 AI 客户端重新连接'
  }
}
watch(() => route.query.state, initialize, { immediate: true })
</script>
