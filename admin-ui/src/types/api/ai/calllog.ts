import type { PageDomain, BaseEntity } from "../common";

/** AI 调用记录分页查询参数 */
export interface AiCallLogQueryParams extends PageDomain {
  /** 场景标识（模糊匹配） */
  scene?: string;
  /** 能力类型 */
  capability?: string;
  /** 状态：SUCCESS / FAILED */
  status?: string;
  /** 链路ID */
  traceId?: string;
  /** 起始时间（由 addDateRange 注入 params.beginTime） */
  beginTime?: string;
  /** 结束时间（由 addDateRange 注入 params.endTime） */
  endTime?: string;
}

/** AI 调用记录 */
export interface AiCallLog extends BaseEntity {
  /** 主键ID */
  id?: number;
  /** 链路ID，一次业务调用一个 */
  traceId?: string;
  /** 场景标识 */
  scene?: string;
  /** 能力类型 */
  capability?: string;
  /** 实际生效的服务商标识 */
  providerCode?: string;
  /** 实际生效的渠道ID */
  channelId?: number;
  /** 实际生效的模型ID */
  modelId?: number;
  /** 第几次尝试，降级会加一 */
  attempt?: number;
  /** 输入 token 数 */
  inputTokens?: number;
  /** 输出 token 数 */
  outputTokens?: number;
  /** 生成图片张数 */
  imageCount?: number;
  /** 耗时（毫秒） */
  elapsedMs?: number;
  /** 首字延迟（毫秒）；仅产生正文的流式调用有值，其余为 null */
  firstTokenMs?: number;
  /** 状态：SUCCESS / FAILED */
  status?: string;
  /** 错误码 */
  errorCode?: string;
  /** 脱敏后的入参摘要 */
  requestDigest?: string;
  /** 触发人 */
  operator?: string;
  /** 场景展示名称（详情接口返回） */
  sceneName?: string;
  /** 服务商展示名（详情接口返回） */
  providerName?: string;
  /** 渠道名称；渠道已删除时为占位（详情接口返回） */
  channelName?: string;
  /** 模型名称；模型已删除时为占位（详情接口返回） */
  modelName?: string;
  /** 生成图资产（详情接口返回，无图时为空数组） */
  images?: AiImageAsset[];
}

/** AI 生成图资产 */
export interface AiImageAsset {
  /** 主键ID */
  id?: number;
  /** 关联的调用记录ID */
  callLogId?: number;
  /** 对象存储键 */
  objectKey?: string;
  /** 永久访问地址 */
  url: string;
  /** 宽 */
  width?: number;
  /** 高 */
  height?: number;
  /** 字节数 */
  sizeBytes?: number;
}

/** 用量统计查询参数 */
export interface AiCallLogStatsParams {
  /** 聚合维度：scene / model / day */
  groupBy?: string;
  /** 起始时间 */
  beginTime?: string;
  /** 结束时间 */
  endTime?: string;
  /** 场景过滤 */
  scene?: string;
}

/** 用量统计结果行 */
export interface AiCallLogStats {
  /** 聚合键：场景标识、模型ID或日期 */
  groupKey?: string;
  /** 调用总次数 */
  totalCount?: number;
  /** 成功次数 */
  successCount?: number;
  /** 失败次数 */
  failedCount?: number;
  /** 输入 token 合计 */
  inputTokens?: number;
  /** 输出 token 合计 */
  outputTokens?: number;
  /** 生成图片张数合计 */
  imageCount?: number;
  /** 平均耗时（毫秒） */
  avgElapsedMs?: number;
}
