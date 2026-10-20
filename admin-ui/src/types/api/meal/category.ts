import type { PageDomain, BaseEntity } from "../common";

/** 分类分页查询参数 */
export interface MealCategoryQueryParams extends PageDomain {
  /** 分类名称（模糊匹配） */
  name?: string;
  /** 状态（0正常 1停用） */
  status?: string;
}

/**
 * 点餐-菜品分类（唯一主类）
 *
 * 固定 9 条平台公共分类（deptId = 0）：岷县、荤菜、半荤、素菜、水产、蛋豆、凉卤、汤羹、面点。
 * 家庭不能新增/删除；「招牌」不是分类，只做点餐端推荐位。
 */
export interface MealCategory extends BaseEntity {
  /** 分类ID */
  categoryId?: number;
  /** 所属家庭ID（0=公共分类） */
  deptId?: number;
  /** 分类名称（9 个唯一主类之一，不允许改名） */
  name?: string;
  /** 分类说明（一句话定位） */
  description?: string;
  /** 分类依据（放什么、不放什么、示例） */
  basis?: string;
  /** 分类图标 */
  icon?: string;
  /** 排序 */
  sort?: number;
  /** 状态（0正常 1停用） */
  status?: "0" | "1";
  /** 删除标记（0存在 2删除） */
  delFlag?: string;
}
