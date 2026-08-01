import type { PageDomain } from "../common";

/** 订阅源分页查询参数（订阅源实体复用 BlogLink） */
export interface BlogFeedQueryParams extends PageDomain {
  /** 站点名称（模糊匹配） */
  linkName?: string;
  /** 状态（0正常 1停用） */
  status?: string;
}

/** 朋友圈条目分页查询参数 */
export interface BlogFeedItemQueryParams extends PageDomain {
  /** 订阅源ID，主从联动时按此过滤；不传则查全部 */
  linkId?: number;
  /** 标题（模糊匹配） */
  title?: string;
  /** 作者（模糊匹配） */
  author?: string;
}

/**
 * 朋友圈条目
 *
 * 由 RSS 同步任务写入，管理端只读与删除，没有新增/修改。
 * 不继承 BaseEntity——该表由机器写入，只有 createTime，没有 createBy/updateBy 等审计字段。
 */
export interface BlogFeedItem {
  /** 主键ID */
  itemId?: number;
  /** 所属订阅源ID */
  linkId?: number;
  /** 条目标题 */
  title?: string;
  /** 作者 */
  author?: string;
  /** 原文链接，同时是去重键 */
  url?: string;
  /** 摘要，已由后端剥离 HTML 标签 */
  summary?: string;
  /** 发布时间 */
  pubDate?: string;
  /** 入库时间 */
  createTime?: string;
  /** 来源站点名，由后端 join 带出，非本表字段 */
  linkName?: string;
}

/** RSS 同步结果 */
export interface BlogFeedSyncResult {
  /** 参与同步的订阅源总数 */
  total: number;
  /** 成功数 */
  success: number;
  /** 失败数（失败源已隔离，不影响其余源） */
  failed: number;
  /** 本次新增条目数 */
  newItems: number;
}
