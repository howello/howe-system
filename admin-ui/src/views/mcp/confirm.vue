<template>
  <main class="mcp-page">
    <section class="mcp-card" aria-labelledby="confirm-title">
      <McpBrand badge="写操作确认" />

      <h1 id="confirm-title">确认这次写入</h1>
      <p class="mcp-intro">请核对目标与后果。确认后服务端会执行一次操作；同一操作重复确认不会重复执行。</p>

      <el-alert v-if="error" :title="error" type="error" :closable="false" class="mcp-error" role="alert" show-icon />

      <template v-if="view">
        <dl class="mcp-detail">
          <div class="mcp-detail__row">
            <dt>操作</dt>
            <dd><code>{{ view.toolName }}</code></dd>
          </div>
          <div class="mcp-detail__row">
            <dt>目标</dt>
            <dd>{{ view.targetType }} <code v-if="view.targetId">{{ view.targetId }}</code></dd>
          </div>
          <div class="mcp-detail__row">
            <dt>当前版本</dt>
            <dd><code>{{ view.expectedVersion || '—' }}</code></dd>
          </div>
          <div class="mcp-detail__row">
            <dt>状态</dt>
            <dd>{{ stateLabel(view.state) }}</dd>
          </div>
        </dl>

        <OperationSummary :summary="view.summary" />

        <section v-if="view.steps && view.steps.length" class="mcp-section" aria-label="后续步骤">
          <div class="mcp-section__head">
            <div>
              <h2 class="mcp-section__title">后续步骤</h2>
              <p class="mcp-section__desc">提交成功后由平台继续执行的固定步骤；结果未确认时不要重复发起。</p>
            </div>
          </div>
          <div class="mcp-section__body">
            <el-table :data="view.steps" row-key="stepKey" size="small">
              <el-table-column label="步骤" min-width="180">
                <template #default="{ row }"><code>{{ row.stepKey }}</code></template>
              </el-table-column>
              <el-table-column label="状态" width="140">
                <template #default="{ row }">{{ stepLabel(row.state) }}</template>
              </el-table-column>
              <el-table-column label="结果" min-width="160">
                <template #default="{ row }">
                  <span v-if="row.state === 'UNKNOWN'" class="mcp-unknown">结果待核对</span>
                  <span v-else class="mcp-muted">{{ row.resultCode || '—' }}</span>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </section>

        <div class="mcp-notice">
          <strong>权限边界：</strong>确认只对当前账号、当前客户端与当前授权有效。
          若对象版本、授权或家庭归属已变化，本次确认会失效并需要重新预览。
        </div>

        <div class="mcp-actions mcp-actions--split">
          <el-button size="large" :disabled="busy || !view.canConfirm" @click="reject">拒绝</el-button>
          <el-button
            size="large"
            type="primary"
            :loading="busy"
            :disabled="!view.canConfirm || busy"
            @click="confirm"
          >确认执行</el-button>
        </div>
      </template>

      <p v-else-if="!error" class="mcp-loading" role="status">正在读取操作详情…</p>

      <router-link class="mcp-back" to="/mcp/operations">
        <el-icon><ArrowLeft /></el-icon> 返回我的操作
      </router-link>
    </section>
    <p class="mcp-page__foot">确认内容只从服务端加载，不使用地址栏中的摘要。</p>
  </main>
</template>

<script setup lang="ts" name="McpConfirm">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { createMcpBrowserClient, mcpErrorMessage, type McpBrowserClient } from '@/api/mcp/browser'
import { McpOperationClient } from '@/api/mcp/operation'
import type { McpOperationView } from '@/types'
import McpBrand from './components/McpBrand.vue'
import OperationSummary from './components/OperationSummary.vue'
import './style.css'

const route = useRoute()
const router = useRouter()
const view = ref<McpOperationView>()
const busy = ref(false)
const error = ref('')
let client: McpOperationClient | undefined

/** 只接受 32 位小写十六进制操作标识。 */
const operationId = String(route.query.operationId ?? '')

/** 状态中文名。 */
function stateLabel(state: string): string {
  const map: Record<string, string> = {
    PREVIEW: '预览', PREPARED: '待确认', QUEUED: '已排队', RUNNING: '执行中', SUCCEEDED: '已成功',
    FAILED: '失败', UNKNOWN: '结果待核对', CANCELLED: '已取消', EXPIRED: '已过期'
  }
  return map[state] ?? state
}

/** 后续步骤状态中文名；未知状态保留原文。 */
function stepLabel(state: string): string {
  const map: Record<string, string> = {
    QUEUED: '排队中', RUNNING: '执行中', SUCCEEDED: '已完成', FAILED: '失败',
    ABORTED: '已终止', CANCELLED: '已取消', UNKNOWN: '结果待核对'
  }
  return map[state] ?? state
}

/** 加载确认详情。 */
async function load(): Promise<void> {
  if (!/^[0-9a-f]{32}$/.test(operationId)) {
    error.value = '操作标识无效，请从“我的操作”重新进入'
    return
  }
  busy.value = true
  try {
    if (!client) {
      const browser: McpBrowserClient = (await createMcpBrowserClient()).client
      client = new McpOperationClient(browser)
    }
    view.value = await client.view(operationId)
  } catch (cause) {
    error.value = mcpErrorMessage(cause)
  } finally {
    busy.value = false
  }
}

/** 创建确认上下文后再提交；只提交行版本与服务端摘要哈希。 */
async function confirm(): Promise<void> {
  if (!client || !view.value || view.value.rowVersion === undefined) return
  busy.value = true
  try {
    const context = await client.context(operationId, view.value.rowVersion)
    await client.confirm(operationId, context.rowVersion, context.summaryHash)
    ElMessage.success('已确认，操作已排队')
    await router.push('/mcp/operations')
  } catch (cause) {
    error.value = mcpErrorMessage(cause)
  } finally {
    busy.value = false
  }
}

/** 拒绝操作，不产生业务效果。 */
async function reject(): Promise<void> {
  if (!client || !view.value || view.value.rowVersion === undefined) return
  busy.value = true
  try {
    await client.reject(operationId, view.value.rowVersion)
    ElMessage.success('已拒绝该操作')
    await router.push('/mcp/operations')
  } catch (cause) {
    error.value = mcpErrorMessage(cause)
  } finally {
    busy.value = false
  }
}

onMounted(load)
</script>
