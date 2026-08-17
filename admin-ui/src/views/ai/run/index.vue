<template>
  <div class="app-container">
    <el-card shadow="never" class="mb8">
      <el-form :inline="true">
        <el-form-item label="Run ID">
          <el-input v-model.number="runIdInput" placeholder="输入运行 ID" clearable style="width: 200px" @keyup.enter="loadRun" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="loadRun">查询</el-button>
          <el-button type="danger" plain :disabled="!run || isTerminal" :loading="cancelling" @click="handleCancel">取消运行</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" v-if="run">
      <template #header><span>运行概览</span></template>
      <el-descriptions :column="3" border>
        <el-descriptions-item label="Run ID">{{ run.runId }}</el-descriptions-item>
        <el-descriptions-item label="会话">{{ run.conversationId }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusType" size="small">{{ run.status }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="Agent">{{ run.agentId }}</el-descriptions-item>
        <el-descriptions-item label="版本">{{ run.agentVersionId }}</el-descriptions-item>
        <el-descriptions-item label="幂等键">{{ run.idempotencyKey }}</el-descriptions-item>
        <el-descriptions-item label="Worker">{{ run.workerId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="租约到期">{{ parseTime(run.leaseUntil) || '-' }}</el-descriptions-item>
        <el-descriptions-item label="恢复次数">{{ run.recoveryCount }}</el-descriptions-item>
        <el-descriptions-item label="开始时间">{{ parseTime(run.startedTime) || '-' }}</el-descriptions-item>
        <el-descriptions-item label="结束时间">{{ parseTime(run.finishedTime) || '-' }}</el-descriptions-item>
        <el-descriptions-item label="错误码">
          <el-tag v-if="run.errorCode" type="danger" size="small">{{ run.errorCode }}</el-tag>
          <span v-else>-</span>
        </el-descriptions-item>
      </el-descriptions>
      <el-collapse class="mt8">
        <el-collapse-item title="快照（路由 / Tool 白名单 / 预算）">
          <pre class="readonly-text">{{ formatJson(run.routeSnapshot) }}</pre>
          <pre class="readonly-text">{{ formatJson(run.toolSnapshot) }}</pre>
          <pre class="readonly-text">{{ formatJson(run.budgetSnapshot) }}</pre>
        </el-collapse-item>
      </el-collapse>
    </el-card>

    <el-card shadow="never" v-if="run" class="mt8">
      <template #header><span>用量与成本</span></template>
      <el-tabs v-model="usageTab">
        <el-tab-pane label="模型调用" name="model">
          <el-table :data="modelCalls" v-loading="usageLoading" size="small">
            <el-table-column label="ID" prop="call_id" width="60" />
            <el-table-column label="渠道" prop="channel_id" width="70" />
            <el-table-column label="模型" prop="model_id" width="70" />
            <el-table-column label="状态" prop="status" width="90">
              <template #default="scope">
                <el-tag size="small" :type="scope.row.status === 'SUCCESS' ? 'success' : 'danger'">{{ scope.row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="usage" prop="usage_json" :show-overflow-tooltip="true" min-width="160" />
            <el-table-column label="价格快照" prop="price_snapshot" :show-overflow-tooltip="true" min-width="140" />
            <el-table-column label="估算成本" prop="estimated_cost" width="100" />
            <el-table-column label="实际成本" prop="actual_cost" width="100" />
            <el-table-column label="错误" prop="error_json" :show-overflow-tooltip="true" min-width="140" />
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="工具调用" name="tool">
          <el-table :data="toolCalls" v-loading="usageLoading" size="small">
            <el-table-column label="ID" prop="tool_call_id" width="70" />
            <el-table-column label="工具" prop="tool_key" width="160" />
            <el-table-column label="状态" prop="status" width="90">
              <template #default="scope">
                <el-tag size="small" :type="scope.row.status === 'SUCCESS' ? 'success' : 'danger'">{{ scope.row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="结果摘要" prop="result_summary" :show-overflow-tooltip="true" min-width="240" />
            <el-table-column label="开始" prop="started_time" width="160">
              <template #default="scope">{{ parseTime(scope.row.started_time) }}</template>
            </el-table-column>
            <el-table-column label="结束" prop="finished_time" width="160">
              <template #default="scope">{{ parseTime(scope.row.finished_time) }}</template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
      <div class="cost-summary" v-if="totalCost !== null">
        累计实际成本：<strong>{{ totalCost }}</strong>
        <span v-if="costUnknown" class="cost-unknown">（部分调用 usage 缺失，成本不可精算）</span>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts" name="AiRun">
import { getRun, cancelRun, listRunUsage, listRunToolUsage } from "@/api/ai/admin"
import { AI_RUN_TERMINAL_STATUSES, type AiRun, type AiRunStatus } from "@/types/api/ai"

const { proxy } = getCurrentInstance() as any
const route = useRoute()

const runIdInput = ref<number | undefined>(undefined)
const run = ref<AiRun | null>(null)
const cancelling = ref(false)
const usageTab = ref<"model" | "tool">("model")
const usageLoading = ref(false)
const modelCalls = ref<any[]>([])
const toolCalls = ref<any[]>([])
const totalCost = ref<number | null>(null)
const costUnknown = ref(false)

const isTerminal = computed(() => !!run.value?.status && AI_RUN_TERMINAL_STATUSES.has(run.value.status as AiRunStatus))

const statusType = computed(() => {
  switch (run.value?.status) {
    case "SUCCEEDED": return "success"
    case "FAILED": case "TIMED_OUT": return "danger"
    case "CANCELLED": case "CANCEL_REQUESTED": return "warning"
    default: return "primary"
  }
})

function loadRun() {
  if (!runIdInput.value) return
  run.value = null
  getRun(runIdInput.value).then((res: any) => {
    run.value = res.data
    loadUsage(runIdInput.value as number)
  })
}

function loadUsage(id: number) {
  usageLoading.value = true
  Promise.all([
    listRunUsage(id, 1, 50).then((r: any) => r.data || []).catch(() => []),
    listRunToolUsage(id, 1, 50).then((r: any) => r.data || []).catch(() => []),
  ]).then(([models, tools]) => {
    modelCalls.value = models
    toolCalls.value = tools
    computeCost(models)
  }).finally(() => { usageLoading.value = false })
}

/** 累加实际成本；usage 缺失标记不可精算（与后端 usage 语义一致，不报零成本）。 */
function computeCost(models: any[]) {
  let sum = 0
  let hasMissing = false
  let anyCost = false
  for (const m of models) {
    if (m.actual_cost != null && !isNaN(Number(m.actual_cost))) {
      sum += Number(m.actual_cost)
      anyCost = true
    } else {
      hasMissing = true
    }
  }
  totalCost.value = anyCost ? Number(sum.toFixed(8)) : null
  costUnknown.value = hasMissing
}

function handleCancel() {
  if (!run.value?.runId) return
  cancelling.value = true
  cancelRun(run.value.runId).then(() => {
    proxy.$modal.msgSuccess("已请求取消")
    loadRun()
  }).finally(() => { cancelling.value = false })
}

function formatJson(s?: string): string {
  if (!s) return ""
  try { return JSON.stringify(JSON.parse(s), null, 2) } catch { return s }
}

// 从路由 query 预填 Run ID（Usage 页跳转而来），并按 query.tab 预选标签页
if (route.query.runId) {
  runIdInput.value = Number(route.query.runId)
  if (route.query.tab === "usage") usageTab.value = "model"
  loadRun()
}

</script>

<style scoped>
.readonly-text {
  white-space: pre-wrap;
  word-break: break-all;
  margin: 4px 0;
  max-height: 200px;
  overflow: auto;
  font-size: 12px;
  font-family: inherit;
  background: var(--el-fill-color-light);
  padding: 8px;
  border-radius: 4px;
}
.cost-summary {
  margin-top: 12px;
  font-size: 14px;
}
.cost-unknown {
  color: var(--el-color-warning);
  margin-left: 8px;
}
.mb8 { margin-bottom: 8px; }
.mt8 { margin-top: 8px; }
</style>
