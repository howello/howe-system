import type { PageDomain, BaseEntity } from "../common";

/** 说说分页查询参数 */
export interface BlogTalkQueryParams extends PageDomain {
  /** 正文（模糊匹配） */
  content?: string;
  /** 标签（模糊匹配） */
  tags?: string;
  /** 是否置顶（0否 1是） */
  isTop?: string;
  /** 状态（0发布 1隐藏） */
  status?: string;
}

/**
 * 博客说说
 *
 * content 是 markdown 原文，录入必须用 MarkdownEditor；
 * 用产出 HTML 的富文本编辑器会把 markdown 语法破坏掉。
 */
export interface BlogTalk extends BaseEntity {
  /** 主键ID */
  talkId?: number;
  /** 正文（markdown 原文） */
  content?: string;
  /** 标签，多个用逗号分隔 */
  tags?: string;
  /** 发布时间，留空时由后端取当前时间 */
  pubDate?: string;
  /** 是否置顶（0否 1是） */
  isTop?: '0' | '1';
  /** 状态（0发布 1隐藏） */
  status?: '0' | '1';
}
