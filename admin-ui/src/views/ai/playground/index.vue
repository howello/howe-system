<template>
   <div class="app-container playground">
      <el-row :gutter="12">
         <!-- 调用参数 -->
         <el-col :xs="24" :md="9" :lg="7">
            <el-card shadow="never" class="panel">
               <template #header>
                  <div class="panel-header">
                     <span>调用参数</span>
                     <el-button link type="primary" icon="RefreshLeft" @click="resetParams">恢复默认</el-button>
                  </div>
               </template>
               <el-form label-position="top">
                  <el-form-item label="能力类型">
                     <el-radio-group v-model="capability" class="capability-group" @change="handleCapabilityChange">
                        <el-radio-button v-for="item in capabilityOptions" :key="item.value" :value="item.value">
                           {{ item.label }}
                        </el-radio-button>
                     </el-radio-group>
                  </el-form-item>
                  <el-form-item label="渠道">
                     <el-select
                        v-model="channelId"
                        placeholder="不指定：走场景路由或全局默认"
                        clearable
                        filterable
                        style="width: 100%"
                        @change="handleChannelChange"
                     >
                        <el-option v-for="item in channelOptions" :key="item.id" :label="item.name" :value="item.id" />
                     </el-select>
                  </el-form-item>
                  <el-form-item label="模型">
                     <el-select v-model="modelName" placeholder="不指定：用该渠道下能力匹配的模型" clearable filterable style="width: 100%">
                        <el-option v-for="item in modelOptions" :key="item.id" :label="modelLabel(item)" :value="item.modelName" />
                     </el-select>
                     <div class="ai-hint">只列出当前渠道与能力下已启用的模型；两个都不选则走全局默认模型。</div>
                  </el-form-item>

                  <template v-if="isChatLike">
                     <el-form-item label="系统提示词">
                        <el-input v-model="systemPrompt" type="textarea" :rows="3" maxlength="2000" placeholder="留空则不发送 system 消息" />
                     </el-form-item>
                     <el-row :gutter="12">
                        <el-col :span="12">
                           <el-form-item label="温度">
                              <el-input-number v-model="temperature" :min="0" :max="2" :step="0.1" :precision="1" controls-position="right" style="width: 100%" />
                           </el-form-item>
                        </el-col>
                        <el-col :span="12">
                           <el-form-item label="最大 token">
                              <el-input-number v-model="maxTokens" :min="1" :max="32000" controls-position="right" style="width: 100%" />
                           </el-form-item>
                        </el-col>
                     </el-row>
                     <div class="ai-hint sampling-hint">这三项会随请求显式下发，会盖掉场景路由里的同名参数；想验证路由自身的参数就把系统提示词清空。</div>
                  </template>

                  <el-form-item v-if="capability === 'CHAT'" label="流式输出">
                     <el-switch v-model="streamEnabled" />
                     <div class="ai-hint">开启后走 SSE 逐字返回；关闭则等完整结果一次返回。</div>
                  </el-form-item>

                  <template v-if="capability === 'IMAGE'">
                     <el-form-item label="尺寸">
                        <el-select v-model="imageSize" style="width: 100%">
                           <el-option v-for="item in sizeOptions" :key="item" :label="item" :value="item" />
                        </el-select>
                     </el-form-item>
                     <el-row :gutter="12">
                        <el-col :span="12">
                           <el-form-item label="生成张数">
                              <el-input-number v-model="imageCount" :min="1" :max="4" controls-position="right" style="width: 100%" />
                           </el-form-item>
                        </el-col>
                        <el-col :span="12">
                           <el-form-item label="随机种子">
                              <el-input-number v-model="seed" :min="0" controls-position="right" style="width: 100%" />
                           </el-form-item>
                        </el-col>
                     </el-row>
                     <el-form-item label="负向提示词">
                        <el-input v-model="negativePrompt" type="textarea" :rows="2" placeholder="留空则不发送" />
                     </el-form-item>
                  </template>

                  <el-form-item v-if="capability === 'EMBEDDING'" label="目标维度">
                     <el-input-number v-model="embedDimensions" :min="1" :max="4096" controls-position="right" style="width: 100%" />
                     <div class="ai-hint">留空用模型默认维度。</div>
                  </el-form-item>
               </el-form>
            </el-card>
         </el-col>

         <!-- 对话与结果 -->
         <el-col :xs="24" :md="15" :lg="17">
            <el-card shadow="never" class="console">
               <template #header>
                  <div class="console-header">
                     <span>{{ capabilityTitle }}</span>
                     <div class="console-tools">
                        <el-tag v-if="activeTarget" size="small" type="info">{{ activeTarget }}</el-tag>
                        <el-button link type="primary" icon="Delete" :disabled="!messages.length" @click="clearMessages">清空</el-button>
                     </div>
                  </div>
               </template>

               <!-- 刻意不加区域级 v-loading：遮罩会盖住正在逐字出现的回复，
                    看起来像「只转圈没输出」。进度由每条消息自己的「生成中」表示 -->
               <div ref="scrollRef" class="message-area">
                  <el-empty v-if="!messages.length" :description="emptyHint" />
                  <div v-for="item in messages" :key="item.id" class="msg" :class="'msg-' + item.role">
                     <div class="msg-avatar">{{ roleLabel(item.role) }}</div>
                     <div class="msg-body">
                        <div class="msg-bubble">
                           <!-- 正文与思考必须和「生成中」并列渲染：之前用 v-else 挂在
                                loading 后面，流式期间整个内容分支都不渲染，于是不管后端
                                推得多细，页面都是转完圈才一次性出现 -->
                           <div v-if="item.reasoning" class="msg-reasoning">
                              <div class="reasoning-head" @click="item.reasoningOpen = !item.reasoningOpen">
                                 <el-icon>
                                    <ArrowRight v-if="!item.reasoningOpen" />
                                    <ArrowDown v-else />
                                 </el-icon>
                                 <span>思考过程（{{ item.reasoning.length }} 字）</span>
                              </div>
                              <div v-show="item.reasoningOpen" class="reasoning-body">{{ item.reasoning }}</div>
                           </div>
                           <div v-if="item.content" class="msg-text">{{ item.content }}</div>
                           <div v-if="item.images?.length" class="msg-images">
                              <el-image
                                 v-for="(url, index) in item.images"
                                 :key="index"
                                 :src="url"
                                 :preview-src-list="item.images"
                                 :initial-index="index"
                                 fit="cover"
                                 class="msg-image"
                              />
                           </div>
                           <div v-if="item.vector" class="msg-vector">
                              <div>向量维度：{{ item.vector.dimensions }}</div>
                              <div>前 {{ item.vector.preview.length }} 个分量：[{{ item.vector.preview.map(toFixed).join(', ') }}]</div>
                           </div>
                           <!-- 状态提示放在内容之后：已经出字时它就是一行进度，还没出字时它就是全部 -->
                           <div v-if="item.loading" class="msg-loading">
                              <el-icon class="is-loading"><Loading /></el-icon>
                              <span>{{ sendingHint }} {{ waitSeconds }}s</span>
                           </div>
                        </div>
                        <div v-if="item.meta" class="msg-meta">
                           <span v-if="item.meta.provider">协议 {{ item.meta.provider }}</span>
                           <span v-if="item.meta.model">模型 {{ item.meta.model }}</span>
                           <span v-if="item.meta.elapsedMs != null">耗时 {{ item.meta.elapsedMs }} ms</span>
                           <span v-if="item.meta.chunkCount != null">分片 {{ item.meta.chunkCount }} 段</span>
                           <span v-if="item.meta.firstDeltaMs != null">首字 {{ item.meta.firstDeltaMs }} ms</span>
                           <span v-if="item.meta.usage?.totalTokens">
                              token {{ item.meta.usage.inputTokens ?? "-" }} 入 / {{ item.meta.usage.outputTokens ?? "-" }} 出 / 共 {{ item.meta.usage.totalTokens }}
                           </span>
                           <span v-if="item.meta.finishReason">结束 {{ item.meta.finishReason }}</span>
                           <span v-if="item.meta.callLogId">调用记录 #{{ item.meta.callLogId }}</span>
                        </div>
                     </div>
                  </div>
               </div>

               <div class="input-area">
                  <el-input
                     v-if="capability === 'VISION'"
                     v-model="imageUrls"
                     type="textarea"
                     :rows="2"
                     resize="none"
                     class="mb8"
                     placeholder="图片地址，一行一个；支持公网 URL 或 data:image/png;base64,...."
                  />
                  <el-input
                     v-model="inputText"
                     type="textarea"
                     :rows="capability === 'EMBEDDING' ? 4 : 3"
                     resize="none"
                     :placeholder="inputPlaceholder"
                     @keydown="handleInputKeydown"
                  />
                  <div class="input-actions">
                     <span class="ai-hint">Enter 发送，Shift + Enter 换行</span>
                     <div>
                        <el-button v-if="sending && capability === 'CHAT' && streamEnabled" plain type="danger" @click="handleCancel">停止</el-button>
                        <el-button type="primary" :loading="sending" @click="handleSend">{{ sending ? "调用中" : "发送" }}</el-button>
                     </div>
                  </div>
               </div>
            </el-card>
         </el-col>
      </el-row>
   </div>
</template>

<script setup lang="ts" name="AiPlayground">
import { chat, chatStream, embed, image, vision } from "@/api/ai/playground"
import { listChannel } from "@/api/ai/channel"
import { listModel } from "@/api/ai/model"
import type { AiChannel, AiChatPayload, AiEmbedPayload, AiEmbedResult, AiImagePayload, AiImageResult, AiMessage, AiModel, AiUsage, AiVisionPayload } from "@/types"

/** 一次调用展示在对话区里的消息 */
interface PlaygroundMessage {
  id: number
  role: "user" | "assistant" | "error"
  content: string
  /** 推理模型在正文之前产出的思考内容 */
  reasoning?: string
  /** 思考内容是否展开 */
  reasoningOpen?: boolean
  /** 用户提供的图片（图片理解）或模型生成的图片（文生图） */
  images?: string[]
  /** 请求进行中 */
  loading?: boolean
  /** 生效目标与用量等附加信息 */
  meta?: MessageMeta
  /** 向量结果的摘要 */
  vector?: { dimensions: number; preview: number[] }
}

/** 调用结果里值得展示的部分 */
interface MessageMeta {
  provider?: string
  model?: string
  elapsedMs?: number
  callLogId?: number
  finishReason?: string
  usage?: AiUsage
  /** 流式：收到的增量段数。只有 1 段说明后端没有逐段推送 */
  chunkCount?: number
  /** 流式：从发出请求到收到第一段增量的毫秒数，接近总耗时说明没有流式效果 */
  firstDeltaMs?: number
}

const { proxy } = getCurrentInstance()

const capabilityOptions = [
  { label: "文本对话", value: "CHAT" },
  { label: "图片理解", value: "VISION" },
  { label: "文生图", value: "IMAGE" },
  { label: "文本向量", value: "EMBEDDING" }
]

const sizeOptions = ["1024*1024", "1280*720", "720*1280", "1440*810", "810*1440"]

/**
 * 面板参数的默认值
 *
 * 初始值与「恢复默认」共用这一份，避免两处各写一遍走样。
 * 种子与向量维度不在这里：它们没有有意义的默认值，留空表示让厂商用自己的默认，
 * 填死反而会限制模型。
 */
const DEFAULT_PARAMS = {
  systemPrompt: "你是一个乐于助人的 AI 助手，回答简洁准确。",
  temperature: 0.7,
  maxTokens: 2048,
  streamEnabled: true,
  imageSize: "1024*1024",
  imageCount: 1,
  negativePrompt: ""
}

const capability = ref<string>("CHAT")
const channelId = ref<number>()
const modelName = ref<string>("")
const systemPrompt = ref<string>(DEFAULT_PARAMS.systemPrompt)
const temperature = ref<number>(DEFAULT_PARAMS.temperature)
const maxTokens = ref<number>(DEFAULT_PARAMS.maxTokens)
const streamEnabled = ref<boolean>(DEFAULT_PARAMS.streamEnabled)
const imageSize = ref<string>(DEFAULT_PARAMS.imageSize)
const imageCount = ref<number>(DEFAULT_PARAMS.imageCount)
const negativePrompt = ref<string>(DEFAULT_PARAMS.negativePrompt)
const seed = ref<number>()
const embedDimensions = ref<number>()

const inputText = ref<string>("")
const imageUrls = ref<string>("")
/** 本次请求已等待的毫秒数：首字之前的空窗期要看得见，否则像卡住 */
const waitMs = ref<number>(0)

const channelOptions = ref<AiChannel[]>([])
const modelOptions = ref<AiModel[]>([])
const messages = ref<PlaygroundMessage[]>([])
const sending = ref<boolean>(false)
const scrollRef = ref<HTMLElement>()

/** 流式调用的取消句柄 */
let streamController: AbortController | null = null
/** 等待计时器 */
let waitTimer: ReturnType<typeof setInterval> | null = null
let messageSeq = 0

/** 已等待秒数，展示在「生成中」旁边 */
const waitSeconds = computed(() => (waitMs.value / 1000).toFixed(1))

const isChatLike = computed(() => capability.value === "CHAT" || capability.value === "VISION")

const capabilityTitle = computed(() => capabilityOptions.find(item => item.value === capability.value)?.label || "")

/** 面板上选中的调用目标，用于在标题栏回显 */
const activeTarget = computed(() => {
  const parts: string[] = []
  if (channelId.value) {
    const channel = channelOptions.value.find((item: AiChannel) => item.id === channelId.value)
    parts.push(channel?.name || "渠道 " + channelId.value)
  }
  if (modelName.value) {
    parts.push(modelName.value)
  }
  return parts.join(" / ")
})

const emptyHint = computed(() => {
  switch (capability.value) {
    case "CHAT":
      return "输入内容后发送，验证该渠道与模型的文本生成"
    case "VISION":
      return "填入图片地址与问题后发送，验证图片理解"
    case "IMAGE":
      return "输入画面描述后发送，验证文生图与图片转存"
    default:
      return "每行一段文本，发送后验证向量维度与取值"
  }
})

const inputPlaceholder = computed(() => {
  switch (capability.value) {
    case "CHAT":
      return "输入要发送给模型的内容"
    case "VISION":
      return "针对上面图片的提问，如「这张图里有什么」"
    case "IMAGE":
      return "画面描述，如「一只在窗台晒太阳的橘猫，暖色调」"
    default:
      return "每行一段待向量化的文本"
  }
})

const sendingHint = computed(() => {
  switch (capability.value) {
    case "IMAGE":
      return "提交并轮询中，文生图较慢，请稍候"
    case "VISION":
      return "识别中"
    case "EMBEDDING":
      return "计算向量中"
    default:
      return streamEnabled.value ? "生成中" : "等待完整结果"
  }
})

/** 角色转展示名 */
function roleLabel(role: PlaygroundMessage["role"]): string {
  if (role === "user") {
    return "我"
  }
  return role === "error" ? "错误" : "AI"
}

/** 模型下拉的展示名 */
function modelLabel(item: AiModel): string {
  const name = item.modelName || ""
  return channelId.value ? name : name + " · " + (item.channelName || "-")
}

/** 向量分量保留 5 位小数 */
function toFixed(value: number): string {
  return value.toFixed(5)
}

/** 查询渠道下拉选项 */
function getChannelOptions() {
  listChannel({ pageNum: 1, pageSize: 200, enabled: 1 }).then(response => {
    channelOptions.value = response.rows
  })
}

/** 按当前渠道与能力查询模型下拉选项 */
function getModelOptions() {
  listModel({ pageNum: 1, pageSize: 200, channelId: channelId.value, capability: capability.value, enabled: 1 }).then(response => {
    modelOptions.value = response.rows
  })
}

/** 切换能力：模型必须重选，否则会拿着 CHAT 的模型去调文生图 */
function handleCapabilityChange() {
  modelName.value = ""
  getModelOptions()
}

/** 切换渠道：模型清单跟着变，已选模型可能不属于新渠道 */
function handleChannelChange() {
  modelName.value = ""
  getModelOptions()
}

/** 追加一条消息并返回它在数组里的响应式引用 */
function pushMessage(role: PlaygroundMessage["role"], content: string, extra?: Partial<PlaygroundMessage>): PlaygroundMessage {
  messages.value.push({ id: ++messageSeq, role, content, ...extra })
  return messages.value[messages.value.length - 1]
}

/** 提取结果里的附加信息 */
function toMeta(result?: {
  provider?: string
  model?: string
  elapsedMs?: number
  callLogId?: number
  finishReason?: string
  usage?: AiUsage
}): MessageMeta | undefined {
  if (!result) {
    return undefined
  }
  return {
    provider: result.provider,
    model: result.model,
    elapsedMs: result.elapsedMs,
    callLogId: result.callLogId,
    finishReason: result.finishReason,
    usage: result.usage
  }
}

/** 组装渠道与模型这两个公共入参 */
function basePayload() {
  return {
    channel: channelId.value ? String(channelId.value) : undefined,
    model: modelName.value || undefined
  }
}

/** 请求收尾：清掉 loading 与发送态；传了 error 就把消息标成失败 */
function settle(message: PlaygroundMessage, error?: unknown) {
  stopWait()
  if (error !== undefined) {
    if ((error as Error)?.name === "AbortError") {
      // 用户主动停止：保留已经收到的增量文本
      message.content = message.content || "已停止生成"
    } else {
      message.role = "error"
      message.content = (error as Error)?.message || "调用失败"
    }
  }
  message.loading = false
  sending.value = false
  streamController = null
}

/** 文本对话 */
async function sendChat() {
  const payload: AiChatPayload = {
    ...basePayload(),
    system: systemPrompt.value.trim() || undefined,
    temperature: temperature.value,
    maxTokens: maxTokens.value,
    messages: historyMessages()
  }
  const reply = pushMessage("assistant", "", { loading: true })
  try {
    if (streamEnabled.value) {
      streamController = new AbortController()
      const startedAt = Date.now()
      let chunkCount = 0
      let firstDeltaMs: number | undefined
      const result = await chatStream(payload, chunk => {
        if (firstDeltaMs === undefined) {
          firstDeltaMs = Date.now() - startedAt
        }
        chunkCount++
        if (chunk.reasoningDelta) {
          // 推理模型先产思考、后产正文。思考默认展开，否则这段等待又是黑盒
          if (reply.reasoning === undefined) {
            reply.reasoning = ""
            reply.reasoningOpen = true
          }
          reply.reasoning += chunk.reasoningDelta
        }
        if (chunk.delta) {
          reply.content += chunk.delta
        }
        // 增量变长会把新字顶到可视区外，逐段跟随才不会看着像卡住
        scrollToBottom()
      }, streamController.signal)
      // 以结束分片里的完整文本为准，避免增量丢包导致少字
      if (result?.text) {
        reply.content = result.text
      }
      reply.meta = { ...(toMeta(result) || {}), chunkCount, firstDeltaMs }
    } else {
      const response = await chat(payload)
      reply.content = response.data?.text || ""
      reply.meta = toMeta(response.data)
    }
  } catch (error) {
    settle(reply, error)
    return
  }
  settle(reply)
}

/** 把已有的对话整理成多轮消息，让模型能看到上下文 */
function historyMessages(): AiMessage[] {
  return messages.value
    .filter((item: PlaygroundMessage) => (item.role === "user" || item.role === "assistant") && item.content)
    .map((item: PlaygroundMessage) => ({ role: item.role, content: item.content }))
}

/** 图片理解 */
async function sendVision(text: string, images: string[]) {
  const payload: AiVisionPayload = {
    ...basePayload(),
    system: systemPrompt.value.trim() || undefined,
    temperature: temperature.value,
    maxTokens: maxTokens.value,
    prompt: text,
    images
  }
  const reply = pushMessage("assistant", "", { loading: true })
  try {
    const response = await vision(payload)
    reply.content = response.data?.text || ""
    reply.meta = toMeta(response.data)
  } catch (error) {
    settle(reply, error)
    return
  }
  settle(reply)
}

/** 文生图 */
async function sendImage(text: string) {
  const payload: AiImagePayload = {
    ...basePayload(),
    prompt: text,
    negativePrompt: negativePrompt.value.trim() || undefined,
    size: imageSize.value || undefined,
    count: imageCount.value,
    seed: seed.value
  }
  const reply = pushMessage("assistant", "", { loading: true })
  try {
    const response = await image(payload)
    const result: AiImageResult | undefined = response.data
    reply.images = (result?.images || []).map(item => item.url).filter((url): url is string => !!url)
    reply.content = result?.prompt ? "实际使用的提示词：" + result.prompt : ""
    reply.meta = toMeta(result)
  } catch (error) {
    settle(reply, error)
    return
  }
  settle(reply)
}

/** 文本向量 */
async function sendEmbed(text: string) {
  const texts = text.split("\n").map(item => item.trim()).filter(Boolean)
  const payload: AiEmbedPayload = {
    ...basePayload(),
    texts,
    dimensions: embedDimensions.value
  }
  const reply = pushMessage("assistant", "", { loading: true })
  try {
    const response = await embed(payload)
    const result: AiEmbedResult | undefined = response.data
    const first = result?.embeddings?.[0]
    if (first?.length) {
      reply.vector = { dimensions: first.length, preview: first.slice(0, 8) }
    } else {
      reply.content = "接口未返回向量"
    }
    reply.meta = toMeta(result)
  } catch (error) {
    settle(reply, error)
    return
  }
  settle(reply)
}

/** 图片地址按行拆开 */
function parseImageUrls(): string[] {
  return imageUrls.value.split("\n").map((item: string) => item.trim()).filter(Boolean)
}

/** 开始等待计时 */
function startWait() {
  stopWait()
  const startedAt = Date.now()
  waitMs.value = 0
  waitTimer = setInterval(() => {
    waitMs.value = Date.now() - startedAt
  }, 100)
}

/** 停止等待计时 */
function stopWait() {
  if (waitTimer !== null) {
    clearInterval(waitTimer)
    waitTimer = null
  }
}

/** 发送 */
async function handleSend() {
  if (sending.value) {
    return
  }
  const text = inputText.value.trim()
  if (!text) {
    proxy.$modal.msgWarning("请先输入内容")
    return
  }
  const images = capability.value === "VISION" ? parseImageUrls() : []
  if (capability.value === "VISION" && !images.length) {
    proxy.$modal.msgWarning("图片理解至少要填一个图片地址")
    return
  }
  pushMessage("user", text, images.length ? { images } : undefined)
  inputText.value = ""
  imageUrls.value = ""
  sending.value = true
  startWait()
  scrollToBottom()
  switch (capability.value) {
    case "CHAT":
      await sendChat()
      break
    case "VISION":
      await sendVision(text, images)
      break
    case "IMAGE":
      await sendImage(text)
      break
    default:
      await sendEmbed(text)
      break
  }
  scrollToBottom()
}

/** 停止流式生成 */
function handleCancel() {
  streamController?.abort()
}

/** 参数恢复默认值 */
function resetParams() {
  systemPrompt.value = DEFAULT_PARAMS.systemPrompt
  temperature.value = DEFAULT_PARAMS.temperature
  maxTokens.value = DEFAULT_PARAMS.maxTokens
  streamEnabled.value = DEFAULT_PARAMS.streamEnabled
  imageSize.value = DEFAULT_PARAMS.imageSize
  imageCount.value = DEFAULT_PARAMS.imageCount
  negativePrompt.value = DEFAULT_PARAMS.negativePrompt
  seed.value = undefined
  embedDimensions.value = undefined
}

/**
 * 输入框按键：Enter 发送，Shift + Enter 换行
 *
 * 中文输入法用 Enter 上屏，这时不能当成发送——isComposing 是标准判断，
 * keyCode 229 兜底老浏览器。
 */
function handleInputKeydown(event: KeyboardEvent) {
  if (event.isComposing || event.keyCode === 229) {
    return
  }
  if (event.key === "Enter" && !event.shiftKey) {
    event.preventDefault()
    handleSend()
  }
}

/** 清空对话区 */
function clearMessages() {
  handleCancel()
  messages.value = []
}

/** 滚到底部，让最新一条可见 */
function scrollToBottom() {
  nextTick(() => {
    const area = scrollRef.value
    if (area) {
      area.scrollTop = area.scrollHeight
    }
  })
}

onBeforeUnmount(() => {
  handleCancel()
  stopWait()
})

getChannelOptions()
getModelOptions()
</script>

<style scoped>
.playground .panel {
   height: calc(100vh - 120px);
   overflow-y: auto;
}

.panel-header {
   display: flex;
   align-items: center;
   justify-content: space-between;
}

.sampling-hint {
   margin: -8px 0 16px;
}

.playground .console {
   height: calc(100vh - 120px);
   display: flex;
   flex-direction: column;
}

.playground .console :deep(.el-card__body) {
   flex: 1;
   min-height: 0;
   display: flex;
   flex-direction: column;
   padding: 12px;
}

.capability-group {
   flex-wrap: wrap;
}

.console-header {
   display: flex;
   align-items: center;
   justify-content: space-between;
}

.console-tools {
   display: flex;
   align-items: center;
   gap: 8px;
}

.message-area {
   flex: 1;
   min-height: 0;
   overflow-y: auto;
   padding-right: 4px;
}

.msg {
   display: flex;
   gap: 8px;
   margin-bottom: 14px;
}

.msg-user {
   flex-direction: row-reverse;
}

.msg-avatar {
   flex: 0 0 32px;
   height: 32px;
   border-radius: 6px;
   display: flex;
   align-items: center;
   justify-content: center;
   font-size: 12px;
   color: #fff;
   background: var(--el-color-info);
}

.msg-user .msg-avatar {
   background: var(--el-color-primary);
}

.msg-error .msg-avatar {
   background: var(--el-color-danger);
}

.msg-body {
   display: flex;
   flex-direction: column;
   max-width: 78%;
   min-width: 0;
}

.msg-user .msg-body {
   align-items: flex-end;
}

.msg-bubble {
   padding: 8px 12px;
   border-radius: 8px;
   background: var(--el-fill-color-light);
   font-size: 13px;
   line-height: 1.6;
   white-space: pre-wrap;
   word-break: break-word;
}

.msg-user .msg-bubble {
   background: var(--el-color-primary-light-9);
}

.msg-error .msg-bubble {
   background: var(--el-color-danger-light-9);
   color: var(--el-color-danger);
}

.msg-loading {
   display: flex;
   align-items: center;
   gap: 6px;
   color: #909399;
}

.msg-images {
   display: flex;
   flex-wrap: wrap;
   gap: 8px;
   margin-top: 8px;
}

.msg-image {
   width: 160px;
   height: 160px;
   border-radius: 6px;
}

.msg-vector {
   font-family: Consolas, Monaco, monospace;
   font-size: 12px;
}

.msg-reasoning {
   margin-bottom: 8px;
   padding-left: 8px;
   border-left: 2px solid var(--el-border-color);
}

.reasoning-head {
   display: flex;
   align-items: center;
   gap: 4px;
   font-size: 12px;
   color: #909399;
   cursor: pointer;
   user-select: none;
}

.reasoning-body {
   margin-top: 4px;
   max-height: 240px;
   overflow-y: auto;
   font-size: 12px;
   line-height: 1.6;
   color: #909399;
   white-space: pre-wrap;
   word-break: break-word;
}

.msg-meta {
   display: flex;
   flex-wrap: wrap;
   gap: 10px;
   margin-top: 4px;
   font-size: 12px;
   color: #909399;
}

.input-area {
   flex: none;
   margin-top: 12px;
   padding-top: 12px;
   border-top: 1px solid var(--el-border-color-lighter);
}

.input-actions {
   display: flex;
   align-items: center;
   justify-content: space-between;
   margin-top: 8px;
}

.ai-hint {
   color: #909399;
   font-size: 12px;
   line-height: 1.5;
}
</style>
