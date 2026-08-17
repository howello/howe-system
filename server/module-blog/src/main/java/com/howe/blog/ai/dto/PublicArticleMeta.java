package com.howe.blog.ai.dto;

import java.util.Date;

/**
 * 博客文章公开元数据：AI 博客 Tool 的对外输出载体。
 *
 * <p>字段白名单刻意排除正文（content）、仓库内文件路径（filePath）、Git SHA（gitSha）、
 * 最后同步时间（lastSyncTime）以及 BaseEntity 的 createBy/createTime/updateBy/updateTime/remark
 * 等审计字段——这些都不允许进入 Tool 输出。公共链接由站点配置 {@code blog.site.url} + slug 拼出。</p>
 *
 * @param slug        文章标识，决定公共 URL /article/{slug}
 * @param title       标题
 * @param categories  分类，逗号分隔
 * @param tags        标签，逗号分隔
 * @param publishDate 发布日期
 * @param summary     摘要（非正文）
 * @param wordCount   正文字数（不含正文本身）
 * @param publicUrl   公共文章链接
 * @param recommend   是否推荐
 * @param hide        是否隐藏（仅从首页/RSS 隐去，文章页仍可访问）
 * @param isTop       是否置顶
 */
public record PublicArticleMeta(
    String slug,
    String title,
    String categories,
    String tags,
    Date publishDate,
    String summary,
    Integer wordCount,
    String publicUrl,
    Boolean recommend,
    Boolean hide,
    Boolean isTop) {
}
