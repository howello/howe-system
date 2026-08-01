import type { PageDomain, BaseEntity } from "../common";

/** 博客友链分页查询参数 */
export interface BlogLinkQueryParams extends PageDomain {
  /** 站点名称（模糊匹配） */
  linkName?: string;
  /** 状态（0正常 1停用） */
  status?: string;
  /** 友链分组（字典 blog_link_group） */
  groupCode?: string;
}

/**
 * 博客站点（友链 / RSS 订阅源）
 *
 * 友链与订阅源共用后端的 blog_link 表，由 linkType 区分（1友链 2RSS订阅源）。
 * linkType 由后端两个控制器各自强制固定，前端无需也不应该传。
 */
export interface BlogLink extends BaseEntity {
  /** 主键ID */
  linkId?: number;
  /** 类型（1友链 2RSS订阅源），由后端固定，前端只读 */
  linkType?: '1' | '2';
  /** 站点名称 */
  linkName?: string;
  /** 站点地址 */
  linkUrl?: string;
  /** 头像/图标地址 */
  avatar?: string;
  /** 站点描述 */
  descr?: string;
  /** 友链分组（字典 blog_link_group，仅友链使用） */
  groupCode?: string;
  /** RSS/Atom 订阅地址（仅订阅源使用） */
  rssUrl?: string;
  /** 最后同步时间（仅订阅源） */
  lastSyncTime?: string;
  /** 最后一次同步失败原因，成功时清空（仅订阅源） */
  lastError?: string;
  /** 状态（0正常 1停用） */
  status?: '0' | '1';
  /** 显示顺序 */
  orderNum?: number;
}
