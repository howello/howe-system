import type { PageDomain, BaseEntity } from "../common";

/** 评价分页查询参数 */
export interface MealReviewQueryParams extends PageDomain {
  /** 订单ID */
  orderId?: number;
  /** 评分（1-5） */
  score?: number;
  /** 评价开始时间 */
  beginTime?: string;
  /** 评价结束时间 */
  endTime?: string;
}

/**
 * 点餐-评价（一单一评）
 */
export interface MealReview extends BaseEntity {
  /** 评价ID */
  reviewId?: number;
  /** 订单ID */
  orderId?: number;
  /** 订单号（列表附带） */
  orderNo?: string;
  /** 所属家庭ID */
  deptId?: number;
  /** 评价人ID */
  userId?: number;
  /** 评价人昵称 */
  userName?: string;
  /** 总体评分（1-5） */
  score?: number;
  /** 评价内容 */
  content?: string;
  /** 图片地址列表 JSON，形如 ["https://..."] */
  images?: string;
  /** 是否匿名（0否 1是） */
  anonymous?: "0" | "1";
  /** 删除标记（0存在 2删除） */
  delFlag?: string;
}
