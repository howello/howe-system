<template>
  <div class="app-container">
    <el-card shadow="never">
      <template #header><span>用量与成本</span></template>
      <el-alert type="info" :closable="false" show-icon class="mb8">
        阶段一用量与成本按 Run 维度统计：进入「运行记录」输入 Run ID，在「用量与成本」标签页查看每次模型调用、工具调用的 Token、价格快照与实际成本。
      </el-alert>
      <el-form :inline="true">
        <el-form-item label="Run ID">
          <el-input v-model.number="runIdInput" placeholder="输入运行 ID" clearable style="width: 200px" @keyup.enter="goRun" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="goRun">查看用量</el-button>
        </el-form-item>
      </el-form>

      <el-divider content-position="left">成本说明</el-divider>
      <ul class="cost-notes">
        <li>每次模型调用记录 usage（prompt/completion tokens）与创建时的价格快照，实际成本按快照计算。</li>
        <li>usage 缺失的调用标记为「成本不可精算」，不会折算为零成本。</li>
        <li>fallback 调用独立计数；Tool 调用记录只读工具的请求摘要与结果。</li>
        <li>完整 Prompt 与响应正文仅短期留存（见参数 <code>ai.retention.debug.payload.days</code>），且默认关闭。</li>
      </ul>
    </el-card>
  </div>
</template>

<script setup lang="ts" name="AiUsage">
const runIdInput = ref<number | undefined>(undefined)

function goRun() {
  if (!runIdInput.value) return
  // 跳转到运行记录页并带入 runId（运行记录页支持路由 query 预填）
  const route = useRoute()
  const router = useRouter()
  router.push({ path: '/ai/run', query: { runId: String(runIdInput.value), tab: 'usage' } })
}
</script>

<style scoped>
.mb8 { margin-bottom: 8px; }
.cost-notes {
  font-size: 13px;
  line-height: 1.9;
  color: var(--el-text-color-regular);
  padding-left: 20px;
}
.cost-notes code {
  background: var(--el-fill-color-light);
  padding: 1px 6px;
  border-radius: 3px;
}
</style>
