import type { BaseEntity, PageDomain, TableDataInfo } from "../common";

/** 运行状态，与后端 RunStatus 枚举一一对应（见 AiAdminController 与 RunStatus）。 */
export type AiRunStatus =
  | "CREATED"
  | "QUEUED"
  | "RUNNING"
  | "CANCEL_REQUESTED"
  | "SUCCEEDED"
  | "FAILED"
  | "CANCELLED"
  | "TIMED_OUT";

/** 终态集合，前端据此停止轮询/SSE 并展示最终结果。 */
export const AI_RUN_TERMINAL_STATUSES: ReadonlySet<AiRunStatus> = new Set([
  "SUCCEEDED",
  "FAILED",
  "CANCELLED",
  "TIMED_OUT",
]);

/** 运行事件：服务端通过 SSE 推送或 events 接口分页返回（字段对应 ai_run_event 列）。 */
export interface AiRunEvent extends BaseEntity {
  /** 事件 ID（SSE 的 id 行，客户端据此去重并携带 Last-Event-ID 重连） */
  eventId?: string;
  /** 运行 ID */
  runId?: number;
  /** Run 内单调序号 */
  sequenceNo?: number;
  /** 事件类型：delta / tool_call / tool_result / route / fallback / usage / cost / status / done / error 等 */
  eventType?: string;
  /** 事件 payload JSON 字符串（前端按 eventType 解析） */
  eventJson?: string;
  /** 幂等键 */
  idempotencyKey?: string;
}

/** Agent 草稿编辑请求体，与后端 AgentDraftRequest 对齐。 */
export interface AgentDraftPayload {
  /** Agent 编码，唯一，小写字母数字与 _- */
  agentKey: string;
  /** 名称 */
  name: string;
  /** 系统提示词 */
  systemPrompt: string;
  /** 路由快照 JSON */
  routeJson?: string;
  /** Tool 白名单 JSON */
  toolJson?: string;
  /** 预算 JSON */
  budgetJson?: string;
}

/** Agent 实体（对应 ai_agent 列，详情/列表返回；草稿字段为 JSON 字符串）。 */
export interface AiAgent extends BaseEntity {
  agentId?: number;
  agentKey?: string;
  name?: string;
  /** 草稿版本号，用于乐观锁冲突检测 */
  draftVersion?: number;
  /** 已发布版本 ID，null 表示从未发布 */
  publishedVersionId?: number;
  /** 状态：'0' 启用 / '1' 停用 */
  status?: "0" | "1";
  /** 草稿 JSON（含 systemPrompt/routeJson/toolJson/budgetJson） */
  draftJson?: string;
}

/** Agent 版本（不可变，对应 ai_agent_version 列）。 */
export interface AiAgentVersion extends BaseEntity {
  versionId?: number;
  agentId?: number;
  versionNo?: number;
  systemPrompt?: string;
  routeSnapshot?: string;
  toolSnapshot?: string;
  capabilitySnapshot?: string;
  budgetSnapshot?: string;
  publishedBy?: string;
  publishedTime?: string;
}

/**
 * 配置资源类型，与后端 AiConfigController 路径参数 {resource} 一一对应。
 * agents 走通用配置接口的列表/启停，草稿编辑/发布/版本走 admin 接口。
 */
export type AiConfigResource =
  | "providers"
  | "channels"
  | "models"
  | "routes"
  | "route-items"
  | "prices"
  | "agents";

/** 配置请求体，与后端 AiConfigRequest 对齐。 */
export interface AiConfigPayload {
  /** 配置编码 */
  key: string;
  /** 名称 */
  name?: string;
  /** 配置 JSON（不同资源含义不同：providers/channels 的 config_json、models 的 capabilities 等） */
  configJson?: string;
  /** 是否启用：'0' 否 / '1' 是 */
  enabled?: "0" | "1";
  /** API Key，仅用于新增或替换，后端绝不回显明文 */
  apiKey?: string;
}

/** 配置实体（通用，对应各配置表的公共列；脱敏摘要只在含密钥的资源上出现）。 */
export interface AiConfig extends BaseEntity {
  [key: string]: any;
}

/** API Key 替换结果：只含脱敏摘要与版本，绝不含明文。 */
export interface AiKeyReplacement {
  /** 脱敏摘要，如 ******abcd */
  keySummary: string;
  /** 密钥版本号 */
  keyVersion: number;
}

/** 创建会话请求体，与后端 ConversationCreateRequest 对齐。 */
export interface ConversationCreatePayload {
  /** Agent 编码（会话绑定一个已发布且未停用的 Agent） */
  agentKey: string;
  /** 会话标题，可空 */
  title?: string;
}

/** 会话实体（对应 ai_conversation 列）。 */
export interface AiConversation extends BaseEntity {
  conversationId?: number;
  agentId?: number;
  userId?: number;
  title?: string;
  /** 会话状态：ACTIVE 等 */
  status?: string;
}

/** 运行实体（对应 ai_run 列）。 */
export interface AiRun extends BaseEntity {
  runId?: number;
  conversationId?: number;
  agentId?: number;
  agentVersionId?: number;
  status?: AiRunStatus;
  /** 路由快照 JSON */
  routeSnapshot?: string;
  /** Tool 白名单快照 JSON */
  toolSnapshot?: string;
  /** 预算快照 JSON */
  budgetSnapshot?: string;
  idempotencyKey?: string;
  workerId?: string;
  leaseUntil?: string;
  heartbeatTime?: string;
  recoveryCount?: number;
  startedTime?: string;
  finishedTime?: string;
  errorCode?: string;
}

/** 模型调用记录（对应 ai_model_call 列，用量成本查询）。 */
export interface AiModelCall extends BaseEntity {
  callId?: number;
  runId?: number;
  channelId?: number;
  modelId?: number;
  status?: string;
  requestStarted?: string;
  requestFinished?: string;
  /** usage JSON 字符串（prompt/completion/total tokens 等） */
  usageJson?: string;
  /** 价格快照 JSON 字符串 */
  priceSnapshot?: string;
  estimatedCost?: number;
  actualCost?: number;
  /** 错误 JSON 字符串（失败分类） */
  errorJson?: string;
}

/** 工具调用记录（对应 ai_tool_call 列）。 */
export interface AiToolCall extends BaseEntity {
  toolCallId?: number;
  runId?: number;
  toolKey?: string;
  idempotencyKey?: string;
  status?: string;
  requestJson?: string;
  resultSummary?: string;
  startedTime?: string;
  finishedTime?: string;
}

/** 会话查询参数。 */
export interface AiConversationQueryParams extends PageDomain {
  keyword?: string;
}

/** 复用分页响应类型别名。 */
export type AiConversationTableData = TableDataInfo<AiConversation[]>;
