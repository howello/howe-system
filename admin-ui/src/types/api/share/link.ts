import type { PageDomain, BaseEntity } from "../common";

/** 分享链接分页查询参数 */
export interface ShareLinkQueryParams extends PageDomain {
  /** 文件名（模糊匹配） */
  fileName?: string;
  /** 短码（精确匹配） */
  code?: string;
  /** 源ID */
  sourceId?: number;
  /** 链接状态（0正常 1已失效 2已焚毁） */
  status?: string;
  /** 缓存状态（0待缓存 1缓存中 2就绪 3失败） */
  cacheStatus?: string;
}

/**
 * 分享记录
 *
 * `cacheStatus` 描述字节缓存进度，`status` 描述链接生死，两者语义分开。
 */
export interface ShareLink extends BaseEntity {
  /** 分享ID */
  linkId?: number;
  /** 短码 */
  code?: string;
  /** 源ID */
  sourceId?: number;
  /** 源名称（列表展示） */
  sourceName?: string;
  /** WebDAV 源文件路径 */
  filePath?: string;
  /** 文件名 */
  fileName?: string;
  /** 扩展名（小写，不含点） */
  fileExt?: string;
  /** 字节数 */
  fileSize?: number;
  /** MIME 类型 */
  contentType?: string;
  /** 存储位置（0本地 1R2） */
  storageType?: "0" | "1";
  /** 存储地址 */
  storageLocation?: string;
  /** 访问密码（仅入参） */
  password?: string;
  /** 是否已设置密码 */
  passwordSet?: boolean;
  /** 过期时间 */
  expireAt?: string;
  /** 最大查看次数（<=0 表示不限） */
  maxViews?: number;
  /** 已查看次数 */
  viewCount?: number;
  /** 阅后即焚（0否 1是） */
  burnAfterRead?: "0" | "1";
  /** 缓存状态（0待缓存 1缓存中 2就绪 3失败） */
  cacheStatus?: "0" | "1" | "2" | "3";
  /** 缓存失败原因 */
  cacheError?: string;
  /** 缓存重试次数 */
  cacheRetryCount?: number;
  /** 链接状态（0正常 1已失效 2已焚毁） */
  status?: "0" | "1" | "2";
  /** 最近访问时间 */
  lastViewTime?: string;
  /** 短链地址（列表展示） */
  shortUrl?: string;
}

/** 创建分享请求 */
export interface ShareLinkCreateRequest {
  /** 源ID */
  sourceId?: number;
  /** WebDAV 源文件路径 */
  filePath?: string;
  /** 访问密码，为空表示无密码 */
  password?: string;
  /** 过期时间，为空表示不过期 */
  expireAt?: string;
  /** 最大查看次数，<=0 表示不限 */
  maxViews?: number;
  /** 阅后即焚（0否 1是） */
  burnAfterRead?: string;
  /** 备注 */
  remark?: string;
}

/** 修改分享请求 */
export interface ShareLinkUpdateRequest {
  /** 分享ID */
  linkId?: number;
  /** 访问密码：null 不改动，空串清除，非空设为该密码 */
  password?: string;
  /** 过期时间，null 表示改为不过期 */
  expireAt?: string;
  /** 最大查看次数，<=0 表示不限 */
  maxViews?: number;
  /** 阅后即焚（0否 1是） */
  burnAfterRead?: string;
  /** 链接状态（0正常 1已失效） */
  status?: string;
  /** 备注 */
  remark?: string;
}

/** 分享访问日志 */
export interface ShareAccessLog {
  /** 日志ID */
  logId?: number;
  /** 分享ID */
  linkId?: number;
  /** 短码 */
  code?: string;
  /** 访问IP */
  ip?: string;
  /** User-Agent */
  userAgent?: string;
  /** 动作（detail/verify/open/file/download） */
  action?: string;
  /** 结果（success/fail） */
  result?: string;
  /** 说明 */
  message?: string;
  /** 访问时间 */
  createTime?: string;
}
