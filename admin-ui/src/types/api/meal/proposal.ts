import type { PageDomain, BaseEntity } from "../common";

/** 提案分页查询参数 */
export interface MealProposalQueryParams extends PageDomain {
  /** 菜名（模糊匹配） */
  name?: string;
  /** 状态（0待审核 1已通过 2已驳回） */
  status?: string;
}

/** 提案审核入参 */
export interface MealProposalAuditBody {
  /** 提案ID */
  proposalId: number;
  /** 审核结果（1通过 2驳回） */
  status: string;
  /** 审核意见 */
  auditRemark?: string;
}

/**
 * 点餐-新菜提案
 *
 * 审核通过时后端会自动在菜品表生成一条菜品（source=1）并回写 dishId。
 */
export interface MealProposal extends BaseEntity {
  /** 提案ID */
  proposalId?: number;
  /** 所属家庭ID */
  deptId?: number;
  /** 提交人ID */
  userId?: number;
  /** 提交人昵称 */
  userName?: string;
  /** 菜名 */
  name?: string;
  /** 期望分类 */
  categoryId?: number;
  /** 期望分类名称（列表附带） */
  categoryName?: string;
  /** 菜品介绍 */
  description?: string;
  /** 参考图 */
  image?: string;
  /** 想吃的理由 */
  reason?: string;
  /** 状态（0待审核 1已通过 2已驳回） */
  status?: string;
  /** 审核人 */
  auditBy?: string;
  /** 审核时间 */
  auditTime?: string;
  /** 审核意见 */
  auditRemark?: string;
  /** 通过后生成的菜品ID */
  dishId?: number;
  /** 删除标记（0存在 2删除） */
  delFlag?: string;
}
