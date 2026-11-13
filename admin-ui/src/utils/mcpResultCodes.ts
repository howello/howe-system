/**
 * MCP 固定结果码的中文展示。
 *
 * 保留原始码，仅补充可理解的说明；未知码不猜测含义，直接展示原值。
 *
 * 审计表保存的是大写枚举名（如 `STATE_CONFLICT`），工具回执使用小写码
 * （如 `state_conflict`），两种写法统一按小写查表。
 */

/** 结果码 → 中文提示；只收录当前后端会返回的固定码。 */
const RESULT_CODE_LABELS: Record<string, string> = {
  ok: '成功',
  accepted: '已受理',
  pending: '处理中',
  'not_found_or_forbidden': '对象不存在或无权访问',
  state_conflict: '对象状态已变化',
  version_conflict: '版本已变化，请重新预览',
  source_version_conflict: '源版本已变化，请重新预览',
  confirmation_required: '需要浏览器确认',
  confirmation_expired: '确认已过期，请重新准备',
  authorization_changed: '授权已变化，请重新授权或准备',
  operation_expired: '操作已过期',
  operation_outcome_unknown: '结果待核对，请勿重新发起',
  idempotency_key_reused: '幂等键已用于其它参数',
  invalid_arguments: '参数不合法',
  invalid_request: '请求不合法',
  invalid_token: '令牌无效',
  invalid_client: '客户端无效',
  invalid_grant: '授权无效',
  invalid_scope: '授权范围无效',
  access_denied: '访问被拒绝',
  execution_denied: '当前账号无权执行',
  tool_unavailable: '工具不可用',
  tool_timeout: '工具执行超时',
  server_busy: '服务繁忙，请稍后重试',
  user_busy: '当前账号并发已满，请稍后重试',
  request_cancelled: '请求已取消',
  result_too_large: '结果超过大小上限',
  invalid_result: '工具结果无效',
  rate_limit_exceeded: '请求过于频繁',
  temporarily_unavailable: '服务暂时不可用',
  unavailable: '服务不可用',
  execution_unknown: '执行结果未知',
  file_reference_expired: '文件引用已过期',
  file_reference_conflict: '文件引用冲突',
  upstream_unavailable: '上游服务不可用',
  internal_error: '内部错误',
  tool_error: '工具执行失败'
}

/**
 * 返回结果码的中文说明，未收录时保留原码。
 *
 * @param code 服务端结果码
 * @returns 中文说明或原码
 */
export function mcpResultCodeLabel(code: string | null | undefined): string {
  if (!code) return '—'
  return RESULT_CODE_LABELS[code.toLowerCase()] ?? code
}

/**
 * 按语义对结果码分组，供标签着色。
 *
 * @param code 服务端结果码
 * @returns Element Plus 标签类型
 */
export function mcpResultCodeType(code: string | null | undefined): 'success' | 'warning' | 'danger' | 'info' {
  const value = (code ?? '').toLowerCase()
  if (value === 'ok' || value === 'accepted') return 'success'
  if (value === 'pending') return 'info'
  if (['rate_limited', 'rate_limit_exceeded', 'server_busy', 'user_busy', 'tool_timeout', 'unavailable',
    'temporarily_unavailable', 'upstream_unavailable'].includes(value)) return 'warning'
  if (!value) return 'info'
  return 'danger'
}
