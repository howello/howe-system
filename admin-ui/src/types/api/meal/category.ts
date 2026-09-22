import type { PageDomain, BaseEntity } from "../common";

/** 分类分页查询参数 */
export interface MealCategoryQueryParams extends PageDomain {
  /** 分类名称（模糊匹配） */
  name?: string;
  /** 状态（0正常 1停用） */
  status?: string;
}

/**
 * 点餐-菜品分类
 *
 * deptId = 0 表示平台公共分类，所有家庭可见；具体家庭 ID 表示该家庭的私有分类。
 */
export interface MealCategory extends BaseEntity {
  /** 分类ID */
  categoryId?: number;
  /** 所属家庭ID（0=公共分类） */
  deptId?: number;
  /** 分类名称 */
  name?: string;
  /** 分类图标 */
  icon?: string;
  /** 排序 */
  sort?: number;
  /** 状态（0正常 1停用） */
  status?: "0" | "1";
  /** 删除标记（0存在 2删除） */
  delFlag?: string;
}
