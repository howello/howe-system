import type { PageDomain, BaseEntity } from "../common";

/** WebDAV 源分页查询参数 */
export interface ShareSourceQueryParams extends PageDomain {
  /** 源名称（模糊匹配） */
  name?: string;
  /** 状态（0正常 1停用） */
  status?: string;
}

/**
 * WebDAV 源配置
 *
 * 密码只写不读：`password` 仅在新增/修改时提交明文，查询接口不返回密码，只返回 `passwordSet`。
 */
export interface ShareSource extends BaseEntity {
  /** 源ID */
  sourceId?: number;
  /** 源名称 */
  name?: string;
  /** WebDAV 根地址 */
  baseUrl?: string;
  /** 账号 */
  username?: string;
  /** 应用密码（仅入参，服务端加密入库） */
  password?: string;
  /** 是否已配置密码 */
  passwordSet?: boolean;
  /** 默认根目录 */
  rootPath?: string;
  /** 状态（0正常 1停用） */
  status?: "0" | "1";
}

/** WebDAV 目录浏览条目 */
export interface WebDavEntry {
  /** 名称 */
  name: string;
  /** 相对源根的完整路径（创建分享时回传） */
  path: string;
  /** 是否目录 */
  directory: boolean;
  /** 字节数 */
  size: number;
  /** MIME 类型 */
  contentType?: string;
  /** 最后修改时间 */
  lastModified?: string;
}
