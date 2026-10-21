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
  /** 创建时间 */
  params?: {
    beginTime?: string;
    endTime?: string;
  };
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
