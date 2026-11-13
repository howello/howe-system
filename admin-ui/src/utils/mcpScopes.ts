/**
 * MCP 授权范围的中文名称、说明与分组。
 *
 * 只维护当前后端真正支持的 scope；新增 scope 时必须同步
 * `module-mcp` 的 `McpScopes.SUPPORTED` 与后端全链路校验。
 */
export interface McpScopeMeta {
  /** 中文名称。 */
  label: string
  /** 用途说明。 */
  description: string
  /** 是否必需。 */
  required?: boolean
  /** 是否默认勾选。 */
  defaultSelected?: boolean
}

/** 已知范围映射；未知范围保留原文，不猜测含义。 */
export const MCP_SCOPE_META: Record<string, McpScopeMeta> = {
  'mcp:connect': {
    label: '连接 MCP 并使用已开放的工具',
    description: '允许客户端在本次会话中调用工具目录里已开放的工具。',
    required: true,
    defaultSelected: true
  },
  offline_access: {
    label: '离线访问',
    description: '允许刷新令牌，使客户端在页面关闭后仍能保持连接。'
  },
  'blog:read': {
    label: '读取博客内容',
    description: '读取当前账号有权管理的文章索引与正文、草稿以及隐藏说说；正文读取不回写索引。'
  },
  'system:self:read': {
    label: '读取本人信息',
    description: '读取当前账号的昵称、角色和家庭归属等最小信息。'
  },
  'meal:read': {
    label: '读取点餐内容',
    description: '读取分类、菜品、招牌菜、本人订单与提案、本人评价和可指派厨师；'
      + '本人列表只含当前账号，详情限本家庭。'
  },
  'share:read': {
    label: '读取分享记录',
    description: '读取当前账号管理范围内的分享约束与缓存状态，不含短链、路径和源地址。'
  },
  'ai:catalog:read': {
    label: '读取 AI 模型目录',
    description: '读取已配置模型的展示名、能力与启停状态，不含渠道、端点或密钥。'
  },
  'ai:logs:read': {
    label: '读取 AI 用量统计',
    description: '读取按天、场景或模型聚合的调用用量；不返回提示词，费用未接入账单时为 null。'
  },
  'system:notice:read': {
    label: '读取我的公告',
    description: '读取正常状态公告与当前账号已读状态，正文只返回纯文本摘要。'
  },
  'system:audit:read': {
    label: '读取服务器健康摘要',
    description: '本期仅开放 CPU、内存、磁盘使用比例与运行时长；不含主机名、IP 和路径。'
  },
  'blog:write': {
    label: '创建与编辑博客内容',
    description: '创建和编辑说说。每次实际写入仍需在浏览器中逐次确认。'
  },
  'system:notice:write': {
    label: '标记公告已读',
    description: '把正常状态公告标记为当前账号已读；任一公告不存在或停用会整批拒绝。'
  },
  'meal:write': {
    label: '点餐下单、取消与评价',
    description: '提交本人订单、取消本人待接单订单、评价本人已完成订单、提交新菜提案；'
      + '每次实际写入仍需在浏览器中逐次确认。'
  },
  'meal:kitchen:read': {
    label: '读取厨师工作台',
    description: '读取本人可处理的待接单与制作中订单，以及对应的待办数量；不含其他家庭数据。'
  },
  'meal:kitchen:write': {
    label: '接单、拒单与完成',
    description: '以当前候选人身份接单或拒单、由实际接单人完成订单；每次操作仍需浏览器确认。'
  },
  'meal:proposal:audit': {
    label: '人工审核新菜提案',
    description: '通过或驳回提案，并联动创建、上架或下架关联菜品；确认页会展示实际影响。'
  },
  'meal:member:read': {
    label: '读取家庭成员',
    description: '家庭管理员可读取本家庭成员的 ID、展示名、家庭角色与状态；'
      + '内置平台管理员沿用原有全量范围。不含账号名与手机号。'
  }
}

/**
 * 返回范围中文名称。
 *
 * @param scope 范围标识
 * @returns 中文名称或原标识
 */
export function mcpScopeLabel(scope: string): string {
  return MCP_SCOPE_META[scope]?.label ?? scope
}

/**
 * 返回范围用途说明。
 *
 * @param scope 范围标识
 * @returns 用途说明或固定兜底
 */
export function mcpScopeDescription(scope: string): string {
  return MCP_SCOPE_META[scope]?.description ?? '该范围暂未提供说明。'
}

/**
 * 判断是否必需范围。
 *
 * @param scope 范围标识
 * @returns 必需时返回 true
 */
export function mcpScopeRequired(scope: string): boolean {
  return MCP_SCOPE_META[scope]?.required === true
}

/**
 * 返回默认勾选的范围。
 *
 * @param scopes 本次请求的全部范围
 * @returns 默认勾选集合
 */
export function mcpDefaultSelected(scopes: string[]): string[] {
  return scopes.filter(scope => MCP_SCOPE_META[scope]?.defaultSelected === true)
}
