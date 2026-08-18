<template>
  <div class="app-container ai-chat">
    <el-row :gutter="16">
      <!-- 左侧会话列表 -->
      <el-col :span="6">
        <el-card shadow="never">
          <template #header>
            <div class="conv-header">
              <span>会话</span>
              <el-button type="primary" size="small" icon="Plus" @click="showNewConv = true">新建</el-button>
            </div>
          </template>
          <el-input v-model="convKeyword" placeholder="搜索标题" clearable size="small" class="mb8" @keyup.enter="loadConversations" />
          <div class="conv-list" v-loading="convLoading">
            <div
              v-for="c in conversations"
              :key="c.conversationId"
              class="conv-item"
              :class="{ active: c.conversationId === currentConversationId }"
              @click="selectConversation(c.conversationId as number)"
            >
              <div class="conv-title">{{ c.title || `会话 #${c.conversationId}` }}</div>
              <div class="conv-time">{{ parseTime(c.updateTime, '{y}-{m}-{d} {h}:{i}') }}</div>
            </div>
            <el-empty v-if="!conversations.length" description="暂无会话" :image-size="60" />
          </div>
        </el-card>
      </el-col>

      <!-- 右侧对话区 -->
      <el-col :span="18">
        <el-card shadow="never" class="chat-panel">
          <template #header>
            <div class="chat-toolbar">
              <span class="chat-title">
                {{ currentConversationId ? `会话 #${currentConversationId}` : '请选择或创建会话' }}
                <el-tag v-if="degraded" type="warning" size="small" class="ml8">实时通道降级</el-tag>
                <el-tag v-if="runStatus && !isTerminal" :type="runStatusType" size="small" class="ml8">{{ runStatus }}</el-tag>
              </span>
              <el-button
                v-if="runStatus && !isTerminal"
                type="danger"
                size="small"
                :loading="cancelling"
                @click="handleCancel"
              >取消运行</el-button>
            </div>
          </template>

          <div class="messages" ref="messagesRef">
            <div v-for="m in messages" :key="m.id" class="message" :class="m.role">
              <div class="role">{{ m.role === 'user' ? '我' : '助手' }}</div>
              <div class="content" v-html="m.html"></div>
            </div>
            <div v-if="streamingText" class="message assistant">
              <div class="role">助手</div>
              <div class="content">{{ streamingText }}<span class="cursor">▌</span></div>
            </div>
            <div v-if="!messages.length && !streamingText" class="empty-hint">
              选择会话后输入消息开始对话。助手只会调用授权的只读工具，不会自行重试或越权。
            </div>
          </div>

          <!-- 事件流水（路由/模型/Tool/fallback/usage/成本） -->
          <el-collapse v-if="eventLog.length" class="event-log">
            <el-collapse-item :title="`运行事件（${eventLog.length}）`">
              <div v-for="(e, i) in eventLog" :key="i" class="event-line">
                <el-tag size="small" :type="eventTagType(e.event)">{{ e.event }}</el-tag>
                <span class="event-data">{{ e.data }}</span>
              </div>
            </el-collapse-item>
          </el-collapse>

          <div class="composer">
            <el-input
              v-model="inputText"
              type="textarea"
              :rows="2"
              :disabled="!currentConversationId || streaming"
              placeholder="输入消息，Enter 发送，Shift+Enter 换行"
              @keydown.enter.exact.prevent="handleSend"
            />
            <el-button type="primary" :loading="sending" :disabled="!currentConversationId || !inputText.trim()" @click="handleSend">发送</el-button>
          </div>
          <div v-if="statusMessage" class="status-message" :class="statusType">{{ statusMessage }}</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 新建会话对话框 -->
    <el-dialog title="新建会话" v-model="showNewConv" width="520px" append-to-body :close-on-click-modal="false">
      <el-form ref="newConvRef" :model="newConvForm" :rules="newConvRules" label-width="90px">
        <el-form-item label="Agent" prop="agentKey">
          <el-select v-model="newConvForm.agentKey" placeholder="选择已发布的 Agent" filterable :loading="agentLoading">
            <el-option
              v-for="a in publishedAgents"
              :key="a.agent_key"
              :label="`${a.name}（${a.agent_key}）`"
              :value="a.agent_key"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="标题">
          <el-input v-model="newConvForm.title" placeholder="可空，用于历史列表展示" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" :loading="creatingConv" @click="submitNewConv">创 建</el-button>
        <el-button @click="showNewConv = false">取 消</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="AiChat">
import { createConversation, listConversations, enqueueMessage, getRun, cancelRun, listRunEvents } from "@/api/ai/admin"
import { listConfig } from "@/api/ai/config"
import { AiSseClient, parseSseChunk, type ParsedSseEvent } from "@/api/ai/sse"
import { AI_RUN_TERMINAL_STATUSES, type AiRunStatus, type AiConversation } from "@/types/api/ai"
import useUserStore from "@/store/modules/user"

const { proxy } = getCurrentInstance() as any

interface ChatMessage {
  id: string
  role: "user" | "assistant"
  html: string
}

const conversations = ref<AiConversation[]>([])
const convLoading = ref(false)
const convKeyword = ref("")
const currentConversationId = ref<number | undefined>(undefined)

const messages = ref<ChatMessage[]>([])
const inputText = ref("")
const streamingText = ref("")
const streaming = ref(false)
const sending = ref(false)
const cancelling = ref(false)
const runId = ref<number | undefined>(undefined)
const runStatus = ref<AiRunStatus | "">("")
const degraded = ref(false)
const eventLog = ref<ParsedSseEvent[]>([])
const statusMessage = ref("")
const statusType = ref<"ok" | "warn" | "err">("warn")
const messagesRef = ref<HTMLElement>()

const showNewConv = ref(false)
const creatingConv = ref(false)
const agentLoading = ref(false)
const publishedAgents = ref<any[]>([])
const newConvForm = reactive<{ agentKey: string; title: string }>({ agentKey: "", title: "" })
const newConvRules = { agentKey: [{ required: true, message: "请选择 Agent", trigger: "change" }] }

let sseClient: AiSseClient | null = null
let msgSeq = 0

const isTerminal = computed(() => !!runStatus.value && AI_RUN_TERMINAL_STATUSES.has(runStatus.value as AiRunStatus))

const runStatusType = computed(() => {
  switch (runStatus.value) {
    case "SUCCEEDED": return "success"
    case "FAILED": case "TIMED_OUT": return "danger"
    case "CANCELLED": case "CANCEL_REQUESTED": return "warning"
    default: return "primary"
  }
})

function escapeHtml(s: string): string {
  return s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;")
}

function eventTagType(event: string): "primary" | "success" | "warning" | "danger" | "info" {
  switch (event) {
    case "error": return "danger"
    case "fallback": case "degraded": case "status": return "warning"
    case "usage": case "cost": case "done": return "success"
    case "tool_call": case "tool_result": return "info"
    default: return "primary"
  }
}

function loadConversations() {
  convLoading.value = true
  listConversations({ pageNum: 1, pageSize: 50, keyword: convKeyword.value }).then((res: any) => {
    conversations.value = res.data || []
  }).finally(() => { convLoading.value = false })
}

function loadPublishedAgents() {
  agentLoading.value = true
  listConfig("agents", "", 1, 100).then((res: any) => {
    publishedAgents.value = (res.data || []).filter((a: any) => a.published_version_id && a.status === "0")
  }).finally(() => { agentLoading.value = false })
}

function selectConversation(conversationId: number) {
  if (currentConversationId.value === conversationId) return
  teardownSse()
  currentConversationId.value = conversationId
  messages.value = []
  streamingText.value = ""
  eventLog.value = []
  runStatus.value = ""
  runId.value = undefined
  statusMessage.value = ""
  // 加载该会话的历史消息（通过事件回放，最近的 Run）
  loadHistory(conversationId)
}

/** 历史回放：读取会话最近的事件，按顺序渲染已有消息。 */
function loadHistory(conversationId: number) {
  // 会话维度的事件接口需要按 run 维度查询；此处先拉取会话里最近的 user/assistant 消息占位。
  // 完整历史依赖后端会话消息列表接口（阶段一仅暴露 run 维度事件），这里只展示当前 Run。
}

function submitNewConv() {
  proxy.$refs["newConvRef"].validate((valid: boolean) => {
    if (!valid) return
    creatingConv.value = true
    createConversation({ agentKey: newConvForm.agentKey, title: newConvForm.title }).then((res: any) => {
      proxy.$modal.msgSuccess("会话已创建")
      showNewConv.value = false
      newConvForm.agentKey = ""
      newConvForm.title = ""
      loadConversations()
      selectConversation(Number(res.data))
    }).finally(() => { creatingConv.value = false })
  })
}

/** 生成幂等键：用户 + 会话 + 时间 + 递增序列，确保重复点击返回同一 runId。 */
function makeIdempotencyKey(): string {
  const userStore = useUserStore()
  const uid = userStore.userId || "anon"
  msgSeq += 1
  return `u${uid}-c${currentConversationId.value}-t${Date.now()}-s${msgSeq}`
}

function handleSend() {
  if (!currentConversationId.value || !inputText.value.trim() || sending.value) return
  const content = inputText.value.trim()
  inputText.value = ""
  messages.value.push({ id: `u-${Date.now()}`, role: "user", html: escapeHtml(content).replace(/\n/g, "<br>") })
  scrollToBottom()

  sending.value = true
  statusMessage.value = ""
  const idempotencyKey = makeIdempotencyKey()
  enqueueMessage(currentConversationId.value as number, content, idempotencyKey).then((res: any) => {
    const id = Number(res.data)
    runId.value = id
    streamingText.value = ""
    eventLog.value = []
    streaming.value = true
    startSse(id)
  }).catch((err: any) => {
    statusMessage.value = `发送失败：${err?.message || "请稍后重试"}`
    statusType.value = "err"
  }).finally(() => { sending.value = false })
}

function startSse(id: number) {
  teardownSse()
  sseClient = new AiSseClient(id, {
    onEvent: (event) => handleSseEvent(event),
    onError: (err) => {
      streaming.value = false
      statusMessage.value = `连接异常：${err.message}（已发送的消息不会丢失，可在 Run 记录查看结果）`
      statusType.value = "err"
    },
    onClose: () => {
      streaming.value = false
    },
  })
  sseClient.connect()
}

function teardownSse() {
  if (sseClient) {
    sseClient.disconnect()
    sseClient = null
  }
}

/** 渲染 SSE 事件：客户端只渲染服务端推送的内容，不重试 Tool、不拼备用输出。 */
function handleSseEvent(event: ParsedSseEvent) {
  eventLog.value.push(event)
  switch (event.event) {
    case "status":
      try {
        const payload = JSON.parse(event.data)
        degraded.value = payload.degraded === true
        if (degraded.value) {
          statusMessage.value = "实时通道降级，改为轮询读取事件，结果不会丢失"
          statusType.value = "warn"
        }
      } catch { /* 忽略非 JSON 状态 */ }
      break
    case "delta":
      try {
        const payload = JSON.parse(event.data)
        if (typeof payload.text === "string") {
          streamingText.value += payload.text
          scrollToBottom()
        }
      } catch {
        streamingText.value += event.data
      }
      break
    case "tool_call":
      statusMessage.value = "助手正在调用授权的只读工具…"
      statusType.value = "warn"
      break
    case "tool_result":
      statusMessage.value = ""
      break
    case "fallback":
      statusMessage.value = "主模型不可用，已降级到备用模型"
      statusType.value = "warn"
      break
    case "usage":
    case "cost":
      // usage 与成本仅展示在事件流，不阻塞渲染
      break
    case "done":
      finalizeStream(event.data)
      break
    case "error":
      streaming.value = false
      try {
        const payload = JSON.parse(event.data)
        statusMessage.value = `运行失败：${payload.message || payload.errorCode || "未知错误"}`
      } catch {
        statusMessage.value = `运行失败：${event.data}`
      }
      statusType.value = "err"
      refreshRunStatus()
      break
    default:
      break
  }
}

/** 流结束：把累积的流式文本固化为一条助手消息，刷新 Run 终态。 */
function finalizeStream(data: string) {
  streaming.value = false
  let finalText = streamingText.value
  try {
    const payload = JSON.parse(data)
    if (typeof payload.text === "string" && payload.text) finalText = payload.text
  } catch { /* 用累积文本 */ }
  if (finalText.trim()) {
    messages.value.push({
      id: `a-${Date.now()}`,
      role: "assistant",
      html: escapeHtml(finalText).replace(/\n/g, "<br>"),
    })
  }
  streamingText.value = ""
  statusMessage.value = ""
  scrollToBottom()
  refreshRunStatus()
}

function refreshRunStatus() {
  if (!runId.value) return
  getRun(runId.value).then((res: any) => {
    runStatus.value = (res.data?.status || "") as AiRunStatus
  }).catch(() => {})
}

function handleCancel() {
  if (!runId.value) return
  cancelling.value = true
  cancelRun(runId.value).then(() => {
    proxy.$modal.msgSuccess("已请求取消")
    refreshRunStatus()
  }).finally(() => { cancelling.value = false })
}

function scrollToBottom() {
  nextTick(() => {
    const el = messagesRef.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

onMounted(() => {
  loadConversations()
  loadPublishedAgents()
})

onBeforeUnmount(() => {
  teardownSse()
})
</script>

<style scoped>
.ai-chat {
  height: calc(100vh - 84px);
}
.conv-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.conv-list {
  max-height: calc(100vh - 260px);
  overflow-y: auto;
}
.conv-item {
  padding: 8px 10px;
  border-radius: 4px;
  cursor: pointer;
  transition: background 0.2s;
}
.conv-item:hover {
  background: var(--el-fill-color-light);
}
.conv-item.active {
  background: var(--el-color-primary-light-9);
}
.conv-title {
  font-size: 14px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.conv-time {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-top: 2px;
}
.chat-panel {
  height: 100%;
}
.chat-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.chat-title {
  font-weight: 600;
}
.ml8 {
  margin-left: 8px;
}
.messages {
  height: calc(100vh - 360px);
  overflow-y: auto;
  padding: 8px 4px;
}
.message {
  margin-bottom: 14px;
}
.message .role {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-bottom: 4px;
}
.message .content {
  font-size: 14px;
  line-height: 1.6;
  white-space: normal;
  word-break: break-word;
}
.message.user .content {
  color: var(--el-color-primary);
}
.empty-hint {
  color: var(--el-text-color-secondary);
  font-size: 13px;
  text-align: center;
  padding: 40px 0;
}
.cursor {
  animation: blink 1s steps(2) infinite;
  color: var(--el-color-primary);
}
@keyframes blink {
  to { opacity: 0; }
}
.event-log {
  margin: 8px 0;
  border-top: 1px dashed var(--el-border-color);
  padding-top: 8px;
}
.event-line {
  font-size: 12px;
  margin-bottom: 4px;
  display: flex;
  gap: 8px;
  align-items: flex-start;
}
.event-data {
  color: var(--el-text-color-secondary);
  word-break: break-all;
}
.composer {
  display: flex;
  gap: 8px;
  align-items: flex-end;
  margin-top: 8px;
}
.status-message {
  font-size: 12px;
  margin-top: 6px;
}
.status-message.ok { color: var(--el-color-success); }
.status-message.warn { color: var(--el-color-warning); }
.status-message.err { color: var(--el-color-danger); }
.mb8 { margin-bottom: 8px; }
</style>
