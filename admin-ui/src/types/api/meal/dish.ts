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
 * 分类是唯一主类：categoryIds 最多 1 个元素，categoryNames 为唯一主类名称；
 * 未绑定主类时 categoryIds 为空、categoryNames 为空（管理端显示「待分类」）。
 * ingredients / steps / tips 在库里是 json，接口层按 JSON 字符串收发。
 */
export interface MealDish extends BaseEntity {
  /** 菜品ID */
  dishId?: number;
  /** 所属家庭ID（0=公共菜谱） */
  deptId?: number;
  /** 关联分类ID列表（最多 1 个主类） */
  categoryIds?: number[];
  /** 分类名称（唯一主类名） */
  categoryNames?: string;
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

/** 一键 AI 生成请求里的「当前已填字段快照」，为空的字段会被补齐 */
export interface MealDishAiCurrent {
  categoryIds?: number[];
  description?: string;
  cover?: string;
  tags?: string;
  duration?: string;
  level?: string;
  serve?: string;
  kcal?: string;
  ingredients?: string;
  steps?: string;
  tips?: string;
}

/** 一键 AI 文本补齐结果：只含被补齐的文本字段与唯一主类匹配结果 */
export interface MealDishAiTextResult {
  /** 本次生成的文本字段集合 */
  fields?: Record<string, string>;
  /** 匹配到的唯一主类ID；未匹配时为 null */
  matchedCategoryId?: number | null;
  /** 模型返回但不在 9 个主类内的名称（仅提示，不落库） */
  unmatchedCategoryName?: string | null;
}

/** 一键 AI 封面生成结果 */
export interface MealDishAiCoverResult {
  /** 新生成的封面永久地址 */
  cover?: string;
  /** 图片未生成时的可读原因 */
  imageError?: string;
}
