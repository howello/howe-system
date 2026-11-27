import type { PageDomain, BaseEntity } from "../common";

/** AI 渠道分页查询参数 */
export interface AiChannelQueryParams extends PageDomain {
  /** 服务商标识 */
  providerCode?: string;
  /** 渠道名称（模糊匹配） */
  name?: string;
  /** 是否启用（0停用 1启用） */
  enabled?: number;
  /** 健康状态：UNKNOWN / UP / DOWN */
  healthStatus?: string;
}

/**
 * AI 渠道
 *
 * apiKey 在列表与详情里都是掩码；编辑时留空表示不修改原密钥。
 */
export interface AiChannel extends BaseEntity {
  /** 主键ID */
  id?: number;
  /** 固定服务商标识 */
  providerCode?: string;
  /** 渠道名称 */
  name?: string;
  /** 接口密钥（后端返回掩码，提交新值时才会覆盖） */
  apiKey?: string;
  /** 接入点，覆盖服务商默认值 */
  baseUrl?: string;
  /** 扩展配置（JSON 对象） */
  extra?: string;
  /** 降级顺序，越小越先 */
  priority?: number;
  /** 是否启用（0停用 1启用） */
  enabled?: number;
  /** 健康状态：UNKNOWN / UP / DOWN */
  healthStatus?: string;
  /** 最近一次连通性检测时间 */
  lastCheckAt?: string;
}

/** AI 服务商（只读，由初始化脚本写入） */
export interface AiProvider {
  code: string;
  name: string;
  protocol: string;
  defaultBaseUrl: string;
}
