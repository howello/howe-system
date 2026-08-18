import { getToken } from '@/utils/auth'

/**
 * 解析后端 RunEventStreamWriter 输出的 SSE 事件流。
 *
 * 后端格式（见 RunEventStreamWriter）：
 *   id: <eventId>\n
 *   event: <type>\n
 *   data: <line>\n          （payload 含换行时多行 data）
 *   \n                       （事件分隔）
 *   event: status\ndata: {"degraded":true}\n\n   （降级状态通知，无 id）
 *   : keep-alive\n\n         （注释，无事件）
 *
 * 解析器是纯函数：输入累积的字节串与上一次消费的位置，输出本轮解析出的事件列表与新位置。
 * 这样既能配合 ReadableStream 的增量读取，也能脱离 DOM 在纯函数测试里验证。
 */
export interface ParsedSseEvent {
  /** SSE 的 id 字段；无 id 的事件（status/注释）为 undefined */
  id?: string
  /** SSE 的 event 字段；默认 'message' */
  event: string
  /** 多行 data 合并后的字符串（换行还原） */
  data: string
}

/**
 * 从缓冲区解析完整事件，返回事件列表与剩余未完成块的起始偏移。
 * 不完整的块（缓冲区末尾缺少空行分隔的片段）留给下一次增量喂入。
 */
export function parseSseChunk(buffer: string): { events: ParsedSseEvent[]; rest: string } {
  const events: ParsedSseEvent[] = []
  let cursor = 0
  while (cursor < buffer.length) {
    // 事件以空行（\n\n）分隔。CRLF 下可能是 \r\n\r\n。
    let sep = buffer.indexOf("\n\n", cursor)
    let sepLen = 2
    if (sep === -1) {
      const crlfSep = buffer.indexOf("\r\n\r\n", cursor)
      if (crlfSep === -1) break
      sep = crlfSep
      sepLen = 4
    }
    const raw = buffer.slice(cursor, sep)
    cursor = sep + sepLen
    const trimmed = raw.replace(/\r/g, "")
    // 注释行（: 开头）不算事件，只起 keep-alive 作用。
    if (trimmed.startsWith(":")) continue
    let id: string | undefined
    let event = "message"
    const dataLines: string[] = []
    for (const line of trimmed.split("\n")) {
      if (line.startsWith("id:")) {
        id = line.slice(3).trim()
      } else if (line.startsWith("event:")) {
        event = line.slice(6).trim()
      } else if (line.startsWith("data:")) {
        dataLines.push(line.slice(5).replace(/^ /, ""))
      }
    }
    // 空块（连续空行）跳过；只有注释或没有 data/event/id 的也跳过。
    if (id === undefined && event === "message" && dataLines.length === 0) continue
    events.push({ id, event, data: dataLines.join("\n") })
  }
  return { events, rest: buffer.slice(cursor) }
}

export interface AiSseHandlers {
  /** 收到一个事件（已按 id 去重） */
  onEvent: (event: ParsedSseEvent) => void
  /** 连接错误（网络、鉴权失败、超时） */
  onError?: (error: Error) => void
  /** 连接关闭（服务端结束或主动断开） */
  onClose?: () => void
}

/**
 * Chat SSE 客户端：基于 fetch + ReadableStream，携带 Bearer token 与 Last-Event-ID 重连。
 *
 * 安全约束（与 plan Task 8 Step 4 一致）：
 * - 客户端不重试 Tool、不改权限、不拼备用完整输出；只渲染服务端推送的事件。
 * - 按 event id 去重，重连时携带 Last-Event-ID 让服务端补发断点之后的事件。
 */
export class AiSseClient {
  private controller: AbortController | null = null
  private lastEventId: string | undefined
  private delivered = new Set<string>()
  private closed = false

  constructor(
    private readonly runId: number,
    private readonly handlers: AiSseHandlers,
  ) {}

  /** 开始订阅；返回后端响应是否成功建立流。 */
  async connect(): Promise<void> {
    this.closed = false
    this.controller = new AbortController()
    const token = getToken()
    const headers: Record<string, string> = {
      Accept: "text/event-stream",
      "Cache-Control": "no-cache",
    }
    if (token) headers["Authorization"] = `Bearer ${token}`
    if (this.lastEventId) headers["Last-Event-ID"] = this.lastEventId

    const baseURL = import.meta.env.VITE_APP_BASE_API
    let response: Response
    try {
      response = await fetch(`${baseURL}/ai/admin/runs/${this.runId}/events/stream`, {
        method: "GET",
        headers,
        signal: this.controller.signal,
      })
    } catch (e) {
      if (!this.closed) this.handlers.onError?.(e as Error)
      return
    }

    if (!response.ok || !response.body) {
      this.handlers.onError?.(new Error(`SSE 连接失败: HTTP ${response.status}`))
      return
    }

    const reader = response.body.getReader()
    const decoder = new TextDecoder("utf-8")
    let buffer = ""

    try {
      while (!this.closed) {
        const { done, value } = await reader.read()
        if (done) break
        buffer += decoder.decode(value, { stream: true })
        const { events, rest } = parseSseChunk(buffer)
        buffer = rest
        for (const event of events) {
          if (event.id) {
            if (this.delivered.has(event.id)) continue
            this.delivered.add(event.id)
            this.lastEventId = event.id
          }
          this.handlers.onEvent(event)
        }
      }
      this.handlers.onClose?.()
    } catch (e) {
      if (!this.closed) this.handlers.onError?.(e as Error)
    }
  }

  /** 主动断开（例如用户离开页面或切换会话）。 */
  disconnect(): void {
    this.closed = true
    this.controller?.abort()
    this.controller = null
  }

  /** 是否已主动关闭。 */
  get isClosed(): boolean {
    return this.closed
  }
}
