import type { McpPublicConfiguration, McpCsrf, McpConsent, McpConnection, McpCall, McpPage, CaptchaInfoResult } from '@/types'

/** 仅保留状态码，不在错误消息中输出凭据或服务端报文。 */
export class McpBrowserError extends Error {
  /** 将独立 API 错误转成固定页面提示。 */
  constructor(public readonly status: number) {
    super(status === 401 ? '登录已过期，请重新登录' : status === 429 ? '操作过于频繁，请稍后重试' : status === 403 ? '请求未通过安全校验，请刷新页面后重试' : '请求失败，请稍后重试')
  }
}

/** Cookie 会话专用客户端，不读取管理端 JWT，也不触发后台重登录弹窗。 */
export class McpBrowserClient {
  private csrf?: McpCsrf
  /** 基础地址只来自公开配置；仅允许 HTTPS 或明确配置的本机 HTTP，拒绝附加查询和凭据。 */
  constructor(readonly baseUrl: string) {
    const url = new URL(baseUrl)
    const localHttp = url.protocol === 'http:' && ['localhost', '127.0.0.1', '[::1]'].includes(url.hostname)
    if ((!localHttp && url.protocol !== 'https:') || url.username || url.password || url.search || url.hash || url.pathname !== '/') {
      throw new Error('MCP 后端地址配置无效')
    }
  }
  /** 发出有超时、无缓存且不自动跟随 API 重定向的独立请求。 */
  async request<T>(path: string, body?: unknown): Promise<T> {
    const headers: Record<string, string> = { Accept: 'application/json' }
    if (body !== undefined) {
      if (!this.csrf) await this.refreshCsrf()
      headers['Content-Type'] = 'application/json'
      headers[this.csrf!.csrfHeaderName] = this.csrf!.csrfToken
    }
    const response = await fetch(this.baseUrl + path, {
      method: body === undefined ? 'GET' : 'POST', credentials: 'include', cache: 'no-store', redirect: 'error',
      headers, body: body === undefined ? undefined : JSON.stringify(body), signal: AbortSignal.timeout(20000)
    })
    if (!response.ok) throw new McpBrowserError(response.status)
    return response.json() as Promise<T>
  }
  /** 登录会轮换 CSRF，因此每次成功登录后必须重新读取。 */
  async refreshCsrf(): Promise<McpCsrf> {
    this.csrf = await this.request<McpCsrf>('/oauth2/login/context')
    return this.csrf
  }
  /** 当前用户信息只供页面显示。 */
  session(): Promise<{ authenticated: boolean; username: string }> { return this.request('/oauth2/self/session') }
  /** 保留现有验证码与人机校验输入。 */
  async login(body: { username: string; password: string; code: string; uuid: string; turnstileToken: string }): Promise<void> {
    await this.request('/oauth2/login', body)
    await this.refreshCsrf()
  }
  /** 获取当前用户所属的待确认请求。 */
  consent(state: string): Promise<McpConsent> { return this.request('/oauth2/self/consent-context?state=' + encodeURIComponent(state)) }
  /** 分页查询本人的连接。 */
  connections(page: number): Promise<McpPage<McpConnection>> { return this.request(`/oauth2/self/connections?page=${page}&pageSize=20`) }
  /** 分页查询本人的事件。 */
  calls(page: number): Promise<McpPage<McpCall>> { return this.request(`/oauth2/self/calls?page=${page}&pageSize=20`) }
  /** 撤销当前账号的一条连接。 */
  revoke(id: number): Promise<unknown> { return this.request(`/oauth2/self/connections/${id}/revoke`, {}) }
  /** 只退出 MCP 浏览器会话。 */
  logout(): Promise<unknown> { return this.request('/oauth2/self/logout', {}) }
  /** 登录后由后端恢复已缓存的 OAuth 请求，忽略页面传入的跳转地址。 */
  resume(): void { window.location.assign(this.baseUrl + '/oauth2/self/resume') }
  /** 用顶层表单提交，允许 OAuth 服务端完成重定向并保留原生错误响应。 */
  async submitConsent(context: McpConsent, scopes: string[]): Promise<void> {
    const csrf = await this.refreshCsrf()
    if (context.submitUrl !== this.baseUrl + '/oauth2/authorize' || scopes.some(scope => !context.scopes.includes(scope))) {
      throw new Error('授权上下文无效，请从 AI 客户端重新连接')
    }
    const form = document.createElement('form')
    form.method = 'POST'
    form.action = context.submitUrl
    const values = [[csrf.csrfParameterName, csrf.csrfToken], ['client_id', context.clientId], ['state', context.state], ...scopes.map(scope => ['scope', scope])]
    for (const [name, value] of values) {
      const input = document.createElement('input')
      input.type = 'hidden'; input.name = name; input.value = value
      form.appendChild(input)
    }
    document.body.appendChild(form)
    form.submit()
    form.remove()
  }
}

/** 经现有同源 API 代理引导配置与验证码，不携带后台 Bearer。 */
async function bootstrapRequest<T>(path: string): Promise<T> {
  const response = await fetch(import.meta.env.VITE_APP_BASE_API + path, {
    credentials: 'omit', cache: 'no-store', redirect: 'error', signal: AbortSignal.timeout(20000)
  })
  if (!response.ok) throw new McpBrowserError(response.status)
  const value = await response.json()
  if (typeof value.code === 'number' && value.code !== 200) throw new McpBrowserError(value.code)
  return value as T
}
/** 获取已冻结的公开地址；运行开关关闭时仍允许退出和撤销。 */
export async function createMcpBrowserClient(): Promise<{ client: McpBrowserClient; configuration: McpPublicConfiguration }> {
  const configuration = await bootstrapRequest<McpPublicConfiguration>('/mcp/public/configuration')
  if (!configuration.installed || !configuration.backendBaseUrl) throw new Error('MCP 尚未安装')
  return { client: new McpBrowserClient(configuration.backendBaseUrl.replace(/\/$/, '')), configuration }
}
/** 获取账号系统原有的验证码策略，不改变人机校验。 */
export function mcpCaptcha(): Promise<CaptchaInfoResult> { return bootstrapRequest('/captchaImage') }
/** 给页面展示固定错误，不输出原始服务器正文。 */
export function mcpErrorMessage(error: unknown): string { return error instanceof McpBrowserError ? error.message : '暂时无法完成操作，请稍后重试' }
