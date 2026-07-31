import type { PageDomain, BaseEntity } from "../common";

/** 博客草稿分页查询参数 */
export interface BlogDraftQueryParams extends PageDomain {
  /** 标题 */
  title?: string;
  /** 文章标识 */
  slug?: string;
  /** 分类 */
  categories?: string;
  /** 标签 */
  tags?: string;
  /** 状态（0草稿 1已发布） */
  status?: string;
}

/** 博客草稿 */
export interface BlogDraft extends BaseEntity {
  /** 草稿ID */
  draftId?: number;
  /** 标题 */
  title?: string;
  /** 计划使用的文章标识 */
  slug?: string;
  /** markdown 正文 */
  content?: string;
  /** 分类 */
  categories?: string;
  /** 标签，多个用逗号分隔 */
  tags?: string;
  /** 封面图地址 */
  cover?: string;
  /** 计划发布日期 */
  publishDate?: string;
  /** 是否推荐（0否 1是） */
  recommend?: '0' | '1';
  /** 是否隐藏（0否 1是） */
  hide?: '0' | '1';
  /** 是否置顶（0否 1是） */
  isTop?: '0' | '1';
  /** 状态（0草稿 1已发布） */
  status?: '0' | '1';
  /** 发布后对应的仓库文件路径 */
  publishedPath?: string;
  /** 发布时间 */
  publishTime?: string;
}
