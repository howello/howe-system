package com.howe.blog.domain.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.howe.blog.domain.BlogArticle;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 文章发布请求
 *
 * <p>
 * 供外部 agent 一次性提交整篇文章：标题、分类、标签、正文全带上，服务端负责拼 frontmatter
 * 并提交到 GitHub。字段命名与 blog-ui 的 content schema 对齐（{@code src/content.config.ts}），
 * 其中 {@code categories} 是单个字符串而不是数组，{@code slug} 对应 frontmatter 的 {@code id}。
 * </p>
 *
 * @author howe
 */
@Data
@Schema(description = "文章发布请求")
public class BlogArticlePublishBody
{
    @Schema(description = "文章标识，决定 URL /article/{slug}，全局唯一", example = "design-pattern-factory",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "文章标识不能为空")
    @Size(max = 128, message = "文章标识长度不能超过128个字符")
    @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._-]*$",
            message = "文章标识只能包含字母、数字、点、下划线和中划线，且以字母或数字开头")
    private String slug;

    @Schema(description = "文章标题", example = "工厂模式｜设计模式", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "文章标题不能为空")
    @Size(max = 255, message = "文章标题长度不能超过255个字符")
    private String title;

    @Schema(description = "分类，blog-ui 的 schema 要求是单个字符串而非数组", example = "设计模式",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "分类不能为空")
    private String categories;

    @Schema(description = "标签列表", example = "[\"设计模式\", \"Java\"]")
    private List<String> tags;

    @Schema(description = "markdown 正文，不含 frontmatter", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "正文不能为空")
    private String content;

    @Schema(description = "发布日期，留空取当前时间", example = "2026-07-31 10:00:00")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date publishDate;

    @Schema(description = "仓库内文件路径，相对文章目录，留空则用 <slug>.md",
            example = "design-pattern/01-factory.md")
    private String filePath;

    @Schema(description = "封面图地址，需为绝对 URL")
    private String cover;

    @Schema(description = "是否推荐", defaultValue = "false")
    private Boolean recommend;

    @Schema(description = "是否隐藏（仅从首页/RSS 隐去，文章页仍可访问，不等于草稿）", defaultValue = "false")
    private Boolean hide;

    @Schema(description = "是否置顶", defaultValue = "false")
    private Boolean top;

    @Schema(description = "标识已存在时是否覆盖。false 时重复提交会报错，避免 agent 重跑把文章改花",
            defaultValue = "false")
    private Boolean overwrite;

    /**
     * 转成索引实体
     *
     * <p>
     * 三个开关在实体里是 {@code "0"}/{@code "1"} 字符串，标签是逗号拼接——
     * 与后台管理页提交的形态保持一致，好让发布走的是同一条写入链路。
     * </p>
     *
     * @return 文章实体
     */
    public BlogArticle toArticle()
    {
        BlogArticle article = new BlogArticle();
        article.setSlug(slug == null ? null : slug.trim());
        article.setTitle(title == null ? null : title.trim());
        article.setCategories(categories == null ? null : categories.trim());
        article.setTags(joinTags());
        article.setContent(content);
        article.setPublishDate(publishDate);
        article.setFilePath(filePath);
        article.setCover(cover);
        article.setRecommend(toFlag(recommend));
        article.setHide(toFlag(hide));
        article.setIsTop(toFlag(top));
        return article;
    }

    /**
     * 是否允许覆盖同标识的已有文章
     */
    public boolean isOverwrite()
    {
        return Boolean.TRUE.equals(overwrite);
    }

    /**
     * 标签列表拼成逗号分隔串，顺带去空白与空项
     */
    private String joinTags()
    {
        if (tags == null || tags.isEmpty())
        {
            return null;
        }
        return tags.stream()
                .filter(tag -> tag != null && !tag.isBlank())
                .map(String::trim)
                .distinct()
                .collect(Collectors.joining(","));
    }

    private static String toFlag(Boolean value)
    {
        return Boolean.TRUE.equals(value) ? "1" : "0";
    }
}
