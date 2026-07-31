import type { PageDomain, BaseEntity } from "../common";

/** 博客文章分页查询参数 */
export interface BlogArticleQueryParams extends PageDomain {
  /** 标题 */
  title?: string;
  /** 文章标识 */
  slug?: string;
  /** 分类 */
  categories?: string;
  /** 标签 */
  tags?: string;
  /** 是否推荐（0否 1是） */
  recommend?: string;
  /** 是否隐藏（0否 1是） */
  hide?: string;
  /** 是否置顶（0否 1是） */
  isTop?: string;
}

/** 博客文章 */
export interface BlogArticle extends BaseEntity {
  /** 文章ID（本地索引主键） */
  articleId?: number;
  /** 文章标识，决定 URL /article/{slug} */
  slug?: string;
  /** 标题 */
  title?: string;
  /** 仓库内文件路径 */
  filePath?: string;
  /** 文件 blob sha */
  gitSha?: string;
  /** 发布日期 */
  publishDate?: string;
  /** 分类（blog-ui 的 schema 里是单个字符串，不是数组） */
  categories?: string;
  /** 标签，多个用逗号分隔 */
  tags?: string;
  /** 封面图地址 */
  cover?: string;
  /** 是否推荐（0否 1是） */
  recommend?: '0' | '1';
  /** 是否隐藏（0否 1是） */
  hide?: '0' | '1';
  /** 是否置顶（0否 1是） */
  isTop?: '0' | '1';
  /** 摘要 */
  summary?: string;
  /** 正文字数 */
  wordCount?: number;
  /** 最后同步时间 */
  lastSyncTime?: string;
  /** markdown 正文，仅详情与提交时携带 */
  content?: string;
}

/** 索引同步结果 */
export interface BlogSyncResult {
  /** 仓库中扫描到的文件数 */
  scanned: number;
  /** 新增索引数 */
  added: number;
  /** 更新索引数 */
  updated: number;
  /** 删除索引数 */
  removed: number;
  /** 跳过数（sha 未变化） */
  skipped: number;
  /** 失败明细 */
  failures: string[];
}
