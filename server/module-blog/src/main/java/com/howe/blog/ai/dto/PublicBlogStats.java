package com.howe.blog.ai.dto;

import java.util.List;

/**
 * 博客公开统计：AI 博客 Tool 的聚合输出载体。
 *
 * <p>只暴露对访客可见的公开口径：已发布文章总数、公开分类分布、公开标签分布、
 * 草稿总数（不含草稿正文与内部路径）。不泄露仓库地址、Git SHA、同步时间或任何审计字段。</p>
 *
 * @param articleTotal     已发布文章总数
 * @param draftTotal       草稿总数（不含正文）
 * @param categoryCounts   公开分类分布（分类名 → 文章数）
 * @param tagCounts        公开标签分布（标签名 → 文章数）
 * @param recentSlugs      最近发布文章的 slug 列表（用于进一步查元数据），按发布时间倒序
 * @param blogAvailable    博客表是否可用（不可用时其余字段为零值或空）
 */
public record PublicBlogStats(
    long articleTotal,
    long draftTotal,
    List<CategoryCount> categoryCounts,
    List<TagCount> tagCounts,
    List<String> recentSlugs,
    boolean blogAvailable) {

    /** 分类分布项 */
    public record CategoryCount(String name, long count) {
    }

    /** 标签分布项 */
    public record TagCount(String name, long count) {
    }
}
