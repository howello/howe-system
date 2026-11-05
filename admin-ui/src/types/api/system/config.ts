import type { PageDomain, BaseEntity } from "../common";

/** 参数配置分页查询参数 */
export interface ConfigQueryParams extends PageDomain {
  /** 参数名称 */
  configName?: string;
  /** 参数键名 */
  configKey?: string;
  /** 系统内置（Y是 N否） */
  configType?: string;
  /** 参数分类（自由文本，仅用于分组展示） */
  paramType?: string;
  /** 是否只查未分类；与 paramType 互斥，paramType 优先 */
  uncategorized?: boolean;
  /** 创建时间 */
  params?: {
    beginTime?: string;
    endTime?: string;
  };
}

/**
 * 参数分类选项
 *
 * 分类是 sys_config.param_type 的自由文本，不是独立字典表。
 * uncategorized=true 的选项代表空 / NULL / 纯空白分类，
 * 选中时应改为传 queryParams.uncategorized=true，而不是传中文字符串。
 */
export interface ConfigParamTypeOption {
  /** 分类值；未分类为空字符串 */
  value: string;
  /** 展示名称 */
  label: string;
  /** 是否为「未分类」选项 */
  uncategorized: boolean;
}

/** 参数配置信息 */
export interface SysConfig extends BaseEntity {
  /** 参数编号 */
  configId?: number;
  /** 参数名称 */
  configName?: string;
  /** 参数键名 */
  configKey?: string;
  /** 参数键值 */
  configValue?: string;
  /** 系统内置（Y是 N否） */
  configType?: 'Y' | 'N';
  /** 参数分类（自由文本，仅用于分组展示） */
  paramType?: string;
  /** 是否敏感（Y是 N否）；Y 时值加密入库，接口只返回掩码 */
  sensitive?: 'Y' | 'N';
}
