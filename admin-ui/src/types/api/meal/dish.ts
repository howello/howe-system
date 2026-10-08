import type { PageDomain, BaseEntity } from "../common";

/** 菜品分页查询参数 */
export interface MealDishQueryParams extends PageDomain {
  /** 菜名（模糊匹配） */
  name?: string;
  /** 关键词（同时匹配菜名与用料） */
  keyword?: string;
  /** 分类ID */
  categoryId?: number;
  /** 状态（0上架 1下架） */
  status?: string;
}

/**
 * 点餐-菜品
 *
 * deptId = 0 表示平台公共菜谱，所有家庭可见。
 * ingredients / steps / tips 在库里是 json，接口层按 JSON 字符串收发。
 */
export interface MealDish extends BaseEntity {
  /** 菜品ID */
  dishId?: number;
  /** 所属家庭ID（0=公共菜谱） */
  deptId?: number;
  /** 分类ID */
  categoryId?: number;
  /** 分类名称（列表附带） */
  categoryName?: string;
  /** 菜名 */
  name?: string;
  /** 封面图地址 */
  cover?: string;
  /** 一句话简介 */
  description?: string;
  /** 标签，逗号分隔 */
  tags?: string;
  /** 耗时 */
  duration?: string;
  /** 难度 */
  level?: string;
  /** 份量 */
  serve?: string;
  /** 热量 */
  kcal?: string;
  /** 用料清单 JSON，形如 [{"name":"五花肉","amount":"600 g"}] */
  ingredients?: string;
  /** 做法步骤 JSON，形如 ["切块","焯水"] */
  steps?: string;
  /** 小贴士 JSON，形如 ["小火慢炖"] */
  tips?: string;
  /** 状态（0上架 1下架） */
  status?: "0" | "1";
  /** 排序 */
  sort?: number;
  /** 来源（0自建 1新菜提案通过） */
  source?: string;
  /** 删除标记（0存在 2删除） */
  delFlag?: string;
}
