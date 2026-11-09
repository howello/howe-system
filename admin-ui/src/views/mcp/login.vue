<template>
  <main class="mcp-page"><section class="mcp-card narrow" aria-labelledby="mcp-title">
    <h1 id="mcp-title">登录 MCP</h1>
    <p>使用系统账号登录，随后确认 AI 客户端申请的访问范围。</p>
    <el-alert v-if="error" :title="error" type="error" :closable="false" class="mcp-error" role="alert" />
    <el-alert v-if="ready && !enabled" title="MCP 暂停接受新授权，已有连接仍可管理和撤销。" type="warning" :closable="false" />
    <el-form label-position="top" @submit.prevent="submit">
        <el-form-item label="账号"><el-input v-model="form.username" aria-label="账号" autocomplete="username" maxlength="30" :disabled="busy" /></el-form-item>
        <el-form-item label="密码"><el-input v-model="form.password" aria-label="密码" type="password" autocomplete="current-password" show-password :disabled="busy" /></el-form-item>
        <el-form-item v-if="captchaEnabled" label="验证码">
          <el-input v-model="form.code" aria-label="验证码" autocomplete="off" maxlength="32" />
          <el-button text @click="refreshCaptcha" :disabled="busy">刷新验证码</el-button>
          <img v-if="codeUrl" :src="codeUrl" alt="登录验证码" height="48" />
        </el-form-item>
        <Turnstile v-if="turnstileEnabled" ref="turnstile" v-model="form.turnstileToken" :site-key="siteKey" @error="challengeError" />
        <div class="mcp-actions"><el-button type="primary" native-type="submit" :loading="busy" :disabled="!ready">登录并继续</el-button></div>
    </el-form>
    <el-button v-if="!ready && error" @click="initialize">重新加载</el-button>
    <p>此处仅登录 MCP 授权页面。已有授权可在“我的连接”中撤销。</p>
  </section></main>
</template>
<script setup lang="ts" name="McpLogin">
import { ref, reactive } from 'vue'
import Turnstile from '@/components/Turnstile/index.vue'
import { createMcpBrowserClient, mcpCaptcha, mcpErrorMessage, McpBrowserError, type McpBrowserClient } from '@/api/mcp/browser'
import './style.css'
const form = reactive({ username: '', password: '', code: '', uuid: '', turnstileToken: '' })
const busy = ref(false), ready = ref(false), enabled = ref(false), error = ref('')
const captchaEnabled = ref(true), turnstileEnabled = ref(false), siteKey = ref(''), codeUrl = ref('')
const turnstile = ref<InstanceType<typeof Turnstile>>()
let client: McpBrowserClient | undefined
/** 人机校验组件错误只展示给当前页面。 */
function challengeError(message: string): void { error.value = message }
/** 失败后刷新图片并丢弃旧答案。 */
async function refreshCaptcha(): Promise<void> {
  try {
    const result = await mcpCaptcha()
    captchaEnabled.value = result.captchaEnabled !== false
    turnstileEnabled.value = result.turnstileEnabled === true
    siteKey.value = result.turnstileSiteKey || ''
    form.code = ''; form.uuid = result.uuid || ''
    codeUrl.value = captchaEnabled.value ? `data:image/${result.imgType || 'png'};base64,${result.img}` : ''
  } catch (cause) { ready.value = false; error.value = mcpErrorMessage(cause) }
}
/** 不存储密码；登录成功后交由后端恢复原请求。 */
async function submit(): Promise<void> {
  if (!client || !ready.value || busy.value) return
  if (!form.username || !form.password || (captchaEnabled.value && !form.code) || (turnstileEnabled.value && !form.turnstileToken)) {
    error.value = '请填写账号、密码并完成验证码或人机校验'; return
  }
  busy.value = true; error.value = ''
  try { await client.login({ ...form }); form.password = ''; client.resume() }
  catch (cause) {
    error.value = cause instanceof McpBrowserError && cause.status === 400 ? '登录失败，请检查账号和验证信息' : mcpErrorMessage(cause)
    form.password = ''; form.turnstileToken = ''; turnstile.value?.reset()
    await refreshCaptcha()
    if (client) await client.refreshCsrf().catch(() => { ready.value = false })
  } finally { busy.value = false }
}
/** 已登录用户直接恢复后端保存的授权请求。 */
async function initialize(): Promise<void> {
  ready.value = false; error.value = ''
  try {
    const result = await createMcpBrowserClient(); client = result.client; enabled.value = result.configuration.enabled
    await client.refreshCsrf()
    try { await client.session(); client.resume(); return }
    catch (cause) { if (!(cause instanceof McpBrowserError) || cause.status !== 401) throw cause }
    await refreshCaptcha()
    ready.value = !error.value
  } catch (cause) { error.value = mcpErrorMessage(cause) }
}
void initialize()
</script>
