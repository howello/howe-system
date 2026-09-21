import type { PageDomain, BaseEntity } from "../common";

/** AI 模型分页查询参数 */
export interface AiModelQueryParams extends PageDomain {
  /** 归属渠道ID */
  channelId?: number;
  /** 能力类型：CHAT / VISION / IMAGE / EMBEDDING */
  capability?: string;
  /** 模型名（模糊匹配） */
  modelName?: string;
  /** 展示名（模糊匹配） */
  displayName?: string;
  /** 是否启用（0停用 1启用） */
  enabled?: number;
}

/** AI 模型 */
export interface AiModel extends BaseEntity {
  /** 主键ID */
  id?: number;
  /** 归属渠道ID */
  channelId?: number;
  /** 能力类型：CHAT / VISION / IMAGE / EMBEDDING */
  capability?: string;
  /** 传给厂商的模型名 */
  modelName?: string;
  /** 展示名 */
  displayName?: string;
  /** 最大输出 token 数 */
  maxTokens?: number;
  /** 扩展配置（JSON 对象） */
  extra?: string;
  /** 是否启用（0停用 1启用） */
  enabled?: number;
  /** 归属渠道名称（后端联表带出，只读） */
  channelName?: string;
  /** 归属渠道的服务商标识（后端联表带出，只读） */
  providerCode?: string;
}

/** 厂商侧返回的模型（「从渠道获取模型」用） */
export interface AiRemoteModel {
  /** 厂商模型名 */
  modelName: string;
  /** 是否已在本渠道下配置 */
  added: boolean;
  /** 后端按模型名推测的能力类型，仅作导入时的默认值 */
  suggestedCapability: string;
}

/** 批量导入的单个模型 */
export interface AiModelImportItem {
  /** 厂商模型名 */
  modelName: string;
  /** 能力类型：CHAT / VISION / IMAGE / EMBEDDING */
  capability: string;
}

/** 批量导入厂商模型请求 */
export interface AiModelImportRequest {
  /** 归属渠道ID */
  channelId: number;
  /** 待导入的模型 */
  models: AiModelImportItem[];
}
