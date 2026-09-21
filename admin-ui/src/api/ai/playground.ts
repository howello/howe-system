import request from '@/utils/request'
import { getToken } from '@/utils/auth'
import type {
  AiChatPayload,
  AiChatResult,
  AiEmbedPayload,
  AiEmbedResult,
  AiImagePayload,
  AiImageResult,
  AiTextChunk,
  AiVisionPayload,
  AjaxResult
} from '@/types'

// 调试台的请求都关掉防重复提交：同一段提示词连测两次是正常操作，被拦下来反而费解
const noRepeat = { repeatSubmit: false }

// 同步对话：生成可能远超全局 10s 超时，这里单独放宽
export function chat(data: AiChatPayload): Promise<AjaxResult<AiChatResult>> {
  return request({
    url: '/ai/playground/chat',
    method: 'post',
    data,
    headers: noRepeat,
    timeout: 120000
  })
}

// 图片理解
export function vision(data: AiVisionPayload): Promise<AjaxResult<AiChatResult>> {
  return request({
    url: '/ai/playground/vision',
    method: 'post',
    data,
    headers: noRepeat,
    timeout: 120000
  })
}

// 文生图：内部要提交、轮询、下载与转存，给足时间
export function image(data: AiImagePayload): Promise<AjaxResult<AiImageResult>> {
  return request({
    url: '/ai/playground/image',
    method: 'post',
    data,
    headers: noRepeat,
    timeout: 300000
  })
}

// 文本向量
export function embed(data: AiEmbedPayload): Promise<AjaxResult<AiEmbedResult>> {
  return request({
    url: '/ai/playground/embed',
    method: 'post',
    data,
    headers: noRepeat,
    timeout: 120000
  })
}

/**
 * 流式对话
 *
 * 不能用 axios：SSE 需要边收边解析，而且 axios 拿不到未完成的分片。
 * 也不能用 EventSource：它发不了 POST、带不了 Authorization 头。
 * 所以这里用 fetch 读响应流，自己按 SSE 规范切事件。
 *
 * @param data     对话请求
 * @param onChunk  每收到一个增量分片回调一次，正文在 delta、思考在 reasoningDelta
 * @param signal   取消信号
 * @returns 结束分片里的完整结果
 */
export async function chatStream(
  data: AiChatPayload,
  onChunk: (chunk: AiTextChunk) => void,
  signal: AbortSignal
): Promise<AiChatResult | undefined> {
  const response = await fetch(import.meta.env.VITE_APP_BASE_API + '/ai/playground/chat/stream', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: 'Bearer ' + getToken()
    },
    body: JSON.stringify(data),
    signal
  })
  // 路由阶段就失败（总开关关闭、模型不可用等）时后端返回的是普通 JSON 而不是事件流
  const contentType = response.headers.get('content-type') || ''
  if (!contentType.includes('text/event-stream')) {
    const body = await response.json().catch(() => null)
    throw new Error(body?.msg || '流式调用失败（HTTP ' + response.status + '）')
  }
  const reader = response.body?.getReader()
  if (!reader) {
    throw new Error('当前浏览器不支持读取流式响应')
  }
  const decoder = new TextDecoder('utf-8')
  let buffer = ''
  let result: AiChatResult | undefined

  /** 处理一个完整的 SSE 事件 */
  const handleEvent = (event: string) => {
    const payload = readEventData(event)
    if (!payload) {
      return
    }
    const chunk = JSON.parse(payload) as AiTextChunk
    // 正文与思考都可能出现，交给调用方分辨
    if (chunk.delta || chunk.reasoningDelta) {
      onChunk(chunk)
    }
    if (chunk.done) {
      if (chunk.errorCode) {
        // 后端会把厂商的真实原因放进 errorMessage，比只看错误码有用得多
        throw new Error("调用失败（" + chunk.errorCode + "）" + (chunk.errorMessage ? "：" + chunk.errorMessage : ""))
      }
      result = chunk.result
    }
  }

  try {
    while (true) {
      const { done, value } = await reader.read()
      if (done) {
        break
      }
      buffer += decoder.decode(value, { stream: true })
      // SSE 事件之间用空行分隔，最后一段可能还没收完，留在 buffer 里等下一轮
      const events = buffer.split(/\r?\n\r?\n/)
      buffer = events.pop() || ''
      events.forEach(handleEvent)
    }
    // 连接结束时 buffer 里可能还剩最后一个没带结尾空行的事件，别把它丢掉
    if (buffer.trim()) {
      handleEvent(buffer)
    }
  } finally {
    // 中途抛错时主动断开，别让服务端继续生成
    reader.cancel().catch(() => {})
  }
  return result
}

/**
 * 取出一个 SSE 事件里的数据行
 *
 * 规范允许一个事件有多行 data，拼接时用换行。后端用 Jackson 输出单行 JSON，
 * 这里按规范处理是为了不依赖那个前提。
 */
function readEventData(event: string): string {
  const dataLines: string[] = []
  for (const line of event.split(/\r?\n/)) {
    if (line.startsWith('data:')) {
      dataLines.push(line.slice(5).replace(/^ /, ''))
    }
  }
  return dataLines.join('\n')
}
