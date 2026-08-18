package com.howe.blog.ai.dto;

import java.util.Date;

/**
 * 博客草稿公开元数据：AI 博客 Tool 的草稿列表/元数据输出载体。
 *
 * <p>排除草稿正文（content）和发布后的仓库文件路径（publishedPath）；草稿是纯本地表，
 * 其 slug 只是「计划使用的文章标识」，尚未对应真实公开链接，因此不带 publicUrl。</p>
 *
 * @param draftId      草稿ID
 * @param title        标题
 * @param slug         计划使用的文章标识（发布时作为 frontmatter 的 id）
 * @param categories   分类，逗号分隔
 * @param tags         标签，逗号分隔
 * @param status       状态（0草稿 1已发布）
 * @param publishDate  计划发布日期
 */
public record PublicDraftMeta(
    Long draftId,
    String title,
    String slug,
    String categories,
    String tags,
    String status,
    Date publishDate) {
}
