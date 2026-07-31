package com.howe.blog.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 文章发布结果
 *
 * @param articleId 本地索引主键
 * @param slug 文章标识
 * @param filePath 仓库内文件路径
 * @param url 文章在博客站上的相对地址
 * @param created true 表示新建，false 表示覆盖了已有文章
 * @author howe
 */
@Schema(description = "文章发布结果")
public record BlogPublishResult(
        @Schema(description = "本地索引主键") Long articleId,

        @Schema(description = "文章标识") String slug,

        @Schema(description = "仓库内文件路径", example = "src/content/blog/design-pattern/01-factory.md")
        String filePath,

        @Schema(description = "文章相对地址", example = "/article/design-pattern-factory") String url,

        @Schema(description = "是否为新建；false 表示覆盖了同标识的已有文章") boolean created)
{
}
