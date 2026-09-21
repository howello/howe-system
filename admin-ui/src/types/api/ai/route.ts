import type { PageDomain, BaseEntity } from "../common";

/** AI 场景路由分页查询参数 */
export interface AiSceneRouteQueryParams extends PageDomain {
  /** 场景标识（模糊匹配） */
  scene?: string;
  /** 能力类型 */
  capability?: string;
  /** 是否启用（0停用 1启用） */
  enabled?: number;
}

/**
 * AI 场景路由
 *
 * fallbackModelIds 与 routeParams 在后端是 JSON 字符串（对应库里的 json 列），
 * 页面上会在表单里转成数组与对象，提交前再转回字符串。
 */
export interface AiSceneRoute extends BaseEntity {
  /** 主键ID */
  id?: number;
  /** 场景标识，如 blog.recipe.cover */
  scene?: string;
  /** 能力类型：CHAT / VISION / IMAGE / EMBEDDING */
  capability?: string;
  /** 主模型ID */
  primaryModelId?: number;
  /** 有序降级链的 JSON 字符串，如 "[3,7]" */
  fallbackModelIds?: string;
  /** 场景级参数覆盖的 JSON 字符串，对应库里的 params 列，如 {"temperature":0.7} */
  routeParams?: string;
  /** 是否启用（0停用 1启用） */
  enabled?: number;
  /** 主模型名（后端联表带出，只读） */
  primaryModelName?: string;
  /** 主模型展示名（后端联表带出，只读） */
  primaryModelDisplayName?: string;
}
