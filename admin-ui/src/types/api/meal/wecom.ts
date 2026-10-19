import type { PageDomain, BaseEntity } from "../common";

/** 企业微信通知配置分页查询参数 */
export interface MealWeComConfigQueryParams extends PageDomain {
  /** 家庭ID */
  deptId?: number;
  /** 启用状态（0停用 1启用） */
  enabled?: string;
}

/**
 * 点餐-企业微信通知配置
 *
 * 每个家庭一条。`webhookKey` 在列表与详情里都是掩码；编辑时留空或回传掩码表示保留原值。
 */
export interface MealWeComConfig extends BaseEntity {
  /** 配置ID */
  configId?: number;
  /** 所属家庭ID */
  deptId?: number;
  /** 家庭名称（查询结果附带，不落库） */
  deptName?: string;
  /** 企业微信群机器人 key（后端返回掩码） */
  webhookKey?: string;
  /** 家庭级总开关（0停用 1启用） */
  enabled?: "0" | "1";
  /** 订单通知开关（0停用 1启用） */
  notifyOrder?: "0" | "1";
  /** AI 通知开关（0停用 1启用） */
  notifyAi?: "0" | "1";
  /** 删除标记（0存在 2删除） */
  delFlag?: string;
}
