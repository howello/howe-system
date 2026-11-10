<template>
  <main class="mcp-page">
    <section class="mcp-card" aria-labelledby="mcp-title">
      <McpBrand badge="安全授权" />

      <h1 id="mcp-title">登录 MCP 授权中心</h1>
      <p class="mcp-intro">使用系统账号登录，随后确认 AI 客户端申请的访问范围。此处只建立 MCP 页面的独立会话，不影响后台登录状态。</p>

      <div class="mcp-steps" aria-hidden="true">
        <span class="mcp-step"><b>1</b>登录系统账号</span>
        <span class="mcp-step"><b>2</b>确认授权范围</span>
        <span class="mcp-step"><b>3</b>客户端开始连接</span>
      </div>

      <el-alert v-if="error" :title="error" type="error" :closable="false" class="mcp-error" role="alert" show-icon />
      <el-alert
        v-if="ready && !enabled"
        title="MCP 暂停接受新授权，已有连接仍可管理和撤销。"
        type="warning"
        :closable="false"
        show-icon
        class="mcp-error"
      />

      <el-form class="mcp-form" label-position="top" @submit.prevent="submit">
        <el-form-item label="账号">
          <el-input
            v-model="form.username"
            aria-label="账号"
            autocomplete="username"
            maxlength="30"
            size="large"
            placeholder="请输入系统账号"
            :disabled="busy"
          >
            <template #prefix><el-icon><User /></el-icon></template>
          </el-input>
        </el-form-item>
        <el-form-item label="密码">
          <el-input
            v-model="form.password"
            aria-label="密码"
            type="password"
            autocomplete="current-password"
            placeholder="请输入登录密码"
            size="large"
            show-password
            :disabled="busy"
          >
            <template #prefix><el-icon><Lock /></el-icon></template>
          </el-input>
        </el-form-item>
        <el-form-item v-if="captchaEnabled" label="验证码">
          <div class="mcp-captcha">
            <el-input
              v-model="form.code"
              aria-label="验证码"
              autocomplete="off"
              maxlength="32"
              size="large"
              placeholder="请输入验证码"
              :disabled="busy"
            />
            <button
              v-if="codeUrl"
              type="button"
              class="mcp-captcha__image"
              :disabled="busy"
              title="点击刷新验证码"
              @click="refreshCaptcha"
            >
              <img :src="codeUrl" alt="登录验证码" />
            </button>
            <button type="button" class="mcp-captcha__refresh" :disabled="busy" @click="refreshCaptcha">
              {{ codeUrl ? '换一张' : '获取验证码' }}
            </button>
          </div>
        </el-form-item>
        <Turnstile v-if="turnstileEnabled" ref="turnstile" v-model="form.turnstileToken" :site-key="siteKey" @error="challengeError" />

        <div class="mcp-actions">
          <el-button
            class="mcp-submit"
            type="primary"
            native-type="submit"
            size="large"
            :loading="busy"
            :disabled="!ready"
          >
            {{ busy ? '正在登录…' : '登录并继续' }}
          </el-button>
        </div>
        <el-button v-if="!ready && error" class="mcp-submit" :loading="busy" @click="initialize">重新加载</el-button>
      </el-form>

      <p class="mcp-hint">已有授权可在「我的连接」中查看和撤销；MCP 页面退出不会注销后台系统。</p>
    </section>
    <p class="mcp-page__foot">连接地址由服务端冻结配置确定，页面不接受任意跳转地址。</p>
  </main>
</template>

<script setup lang="ts" name="McpLogin">
import { ref, reactive } from 'vue'
import { Lock, User } from '@element-plus/icons-vue'
import Turnstile from '@/components/Turnstile/index.vue'
import { createMcpBrowserClient, mcpCaptcha, mcpErrorMessage, McpBrowserError, type McpBrowserClient } from '@/api/mcp/browser'
import McpBrand from './components/McpBrand.vue'
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
