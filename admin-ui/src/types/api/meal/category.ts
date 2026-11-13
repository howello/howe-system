import type { PageDomain, BaseEntity } from "../common";

/** 分类分页查询参数 */
export interface MealCategoryQueryParams extends PageDomain {
  /** 分类名称（模糊匹配） */
  name?: string;
  /** 状态（0正常 1停用） */
  status?: string;
  /** 归属家庭ID；仅平台管理员可指定 */
  deptId?: number;
}

/**
 * 点餐-菜品分类
 *
 * 公共分类（deptId = 0）固定 9 个：荤菜、半荤、素菜、水产、蛋豆、凉卤、汤羹、面点，
 * 名称与数量锁定，仅平台管理员可改说明、依据、图标、排序、状态。
 * 家庭私有分类（deptId = 家庭ID）由家庭管理员与平台超管增删改。
 * 「招牌」不是分类，只做点餐端推荐位。
 */
export interface MealCategory extends BaseEntity {
  /** 分类ID */
  categoryId?: number;
  /** 所属家庭ID（0=公共分类） */
  deptId?: number;
  /** 归属家庭名称（列表展示用，后端不落库） */
  deptName?: string;
  /** 分类名称（公共分类为 9 个锁定名称之一） */
  name?: string;
  /** 分类说明（一句话定位） */
  description?: string;
  /** 分类依据（放什么、不放什么、示例） */
  basis?: string;
  /** 分类图标（emoji） */
  icon?: string;
  /** 排序 */
  sort?: number;
  /** 状态（0正常 1停用） */
  status?: "0" | "1";
  /** 删除标记（0存在 2删除） */
  delFlag?: string;
}

/** 分类保存请求 */
export interface MealCategorySaveRequest {
  /** 归属家庭ID；仅平台管理员新增时必填 */
  deptId?: number;
  name: string;
  description: string;
  basis: string;
  icon?: string;
  sort?: number;
  status?: "0" | "1";
  remark?: string;
}

/** AI 审阅状态 */
export type MealCategoryReviewStatus = "APPROVED" | "REJECTED" | "SKIPPED";

/** 被 AI 调整的字段差异 */
export interface MealCategoryFieldChange {
  field: string;
  before: string;
  after: string;
}

/** 分类保存结果（含 AI 审阅结论） */
export interface MealCategorySaveResult {
  /** AI 是否通过；跳过审阅时为 null */
  approved: boolean | null;
  /** 是否已落库 */
  saved: boolean;
  /** 审阅状态 */
  reviewStatus: MealCategoryReviewStatus;
  /** 拒绝原因 */
  reason?: string;
  /** 修改示例（最多 3 条） */
  examples: string[];
  /** 实际保存的分类 */
  category?: MealCategory;
  /** 被 AI 调整的字段差异 */
  changes: MealCategoryFieldChange[];
  /** 随分类一起迁移归属的菜品数量（仅超管改归属时返回） */
  movedDishCount?: number | null;
}
