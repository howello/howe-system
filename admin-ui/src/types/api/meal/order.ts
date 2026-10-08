import type { PageDomain, BaseEntity } from "../common";

/** 订单分页查询参数 */
export interface MealOrderQueryParams extends PageDomain {
  /** 订单号（模糊匹配） */
  orderNo?: string;
  /** 状态（0待接单 1制作中 2已完成 3已取消） */
  status?: string;
  /** 点餐人昵称（模糊匹配） */
  userName?: string;
  /** 下单开始时间 */
  beginTime?: string;
  /** 下单结束时间 */
  endTime?: string;
}

/** 订单明细（菜品快照） */
export interface MealOrderItem {
  itemId?: number;
  orderId?: number;
  dishId?: number;
  dishName?: string;
  dishCover?: string;
  count?: number;
  remark?: string;
}

/**
 * 点餐-订单
 *
 * 只有下单时间（createTime），没有预约用餐时间。
 */
export interface MealOrder extends BaseEntity {
  /** 订单ID */
  orderId?: number;
  /** 订单号 */
  orderNo?: string;
  /** 所属家庭ID */
  deptId?: number;
  /** 点餐人ID */
  userId?: number;
  /** 点餐人昵称 */
  userName?: string;
  /** 状态（0待接单 1制作中 2已完成 3已取消） */
  status?: string;
  /** 菜品总份数 */
  totalCount?: number;
  /** 整体备注 */
  orderRemark?: string;
  /** 接单人 */
  acceptBy?: string;
  /** 接单时间 */
  acceptTime?: string;
  /** 完成人 */
  finishBy?: string;
  /** 完成时间 */
  finishTime?: string;
  /** 取消时间 */
  cancelTime?: string;
  /** 点餐明细（详情接口附带） */
  items?: MealOrderItem[];
  /** 已等待分钟数（后端计算） */
  waitMinutes?: number;
  /** 已制作分钟数（后端计算） */
  cookMinutes?: number;
}
