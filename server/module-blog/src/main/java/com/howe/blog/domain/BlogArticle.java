package com.howe.blog.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.howe.common.annotation.Excel;
import com.howe.common.annotation.Excel.ColumnType;
import com.howe.common.core.domain.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import java.util.Date;

/**
 * 博客文章索引 blog_article
 *
 * <p>
 * 只是本地索引：真正的文章内容在 GitHub 仓库的 markdown 文件里。列表查询走这张表，
 * 正文由 {@code content} 字段承载但不落库——它只在读取详情和提交保存时被填充。
 * </p>
 *
 * @author howe
 */
@Schema(description = "博客文章索引，正文真源在 GitHub 仓库的 markdown 文件，本表只是本地索引")
public class BlogArticle extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 文章ID（本地索引主键） */
    @Schema(description = "文章ID（本地索引主键）", example = "1")
    @Excel(name = "文章ID", cellType = ColumnType.NUMERIC)
    private Long articleId;

    /** 文章标识，决定 URL /article/{slug} */
    @Schema(description = "文章标识，决定文章 URL /article/{slug}，全局唯一", example = "interview-notes-jvm")
    @Excel(name = "文章标识")
    private String slug;

    /** 文章标题 */
    @Schema(description = "文章标题", example = "JVM 原理｜java知识点")
    @Excel(name = "标题")
    private String title;

    /** 仓库内文件路径 */
    @Schema(description = "仓库内文件路径，相对仓库根", example = "src/content/blog/interview-notes/01-jvm.md")
    @Excel(name = "文件路径")
    private String filePath;

    /** 文件 blob sha，GitHub 写操作的乐观锁凭据 */
    @Schema(description = "文件 blob sha，GitHub 写操作的乐观锁凭据，写和删必须带上，过期会返回 409", example = "9daeafb9864cf43055ae93beb0afd6c7d144bfa4")
    private String gitSha;

    /** 发布日期 */
    @Schema(description = "发布日期", example = "2026-03-30 11:26:55")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "发布日期", dateFormat = "yyyy-MM-dd")
    private Date publishDate;

    /** 分类，多个用逗号分隔 */
    @Schema(description = "分类，多个用逗号分隔", example = "java知识点")
    @Excel(name = "分类")
    private String categories;

    /** 标签，多个用逗号分隔 */
    @Schema(description = "标签，多个用逗号分隔", example = "java知识点,Java")
    @Excel(name = "标签")
    private String tags;

    /** 封面图地址 */
    @Schema(description = "封面图地址", example = "https://img.wyantao.com/cover.png")
    private String cover;

    /** 是否推荐（0否 1是） */
    @Schema(description = "是否推荐（0否 1是）", example = "0")
    @Excel(name = "推荐", readConverterExp = "0=否,1=是")
    private String recommend;

    /** 是否隐藏（0否 1是） */
    @Schema(description = "是否隐藏（0否 1是），只是从首页/RSS/上下篇隐去，文章页仍可访问，不等于草稿", example = "0")
    @Excel(name = "隐藏", readConverterExp = "0=否,1=是")
    private String hide;

    /** 是否置顶（0否 1是） */
    @Schema(description = "是否置顶（0否 1是）", example = "0")
    @Excel(name = "置顶", readConverterExp = "0=否,1=是")
    private String isTop;

    /** 摘要 */
    @Schema(description = "摘要")
    private String summary;

    /** 正文字数 */
    @Schema(description = "正文字数", example = "1280")
    @Excel(name = "字数", cellType = ColumnType.NUMERIC)
    private Integer wordCount;

    /** 最后一次与仓库对齐的时间 */
    @Schema(description = "最后一次与仓库对齐的时间", example = "2026-07-30 09:00:00")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date lastSyncTime;

    /** markdown 正文，不落库：读详情时从 GitHub 拉取，保存时写回 GitHub */
    @Schema(description = "markdown 正文，不落库：读详情时从 GitHub 拉取，保存时写回 GitHub")
    private String content;

    public Long getArticleId()
    {
        return articleId;
    }

    public void setArticleId(Long articleId)
    {
        this.articleId = articleId;
    }

    @NotBlank(message = "文章标识不能为空")
    @Size(min = 0, max = 128, message = "文章标识长度不能超过128个字符")
    @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._-]*$", message = "文章标识只能包含字母、数字、点、下划线和中划线，且以字母或数字开头")
    public String getSlug()
    {
        return slug;
    }

    public void setSlug(String slug)
    {
        this.slug = slug;
    }

    @NotBlank(message = "文章标题不能为空")
    @Size(min = 0, max = 255, message = "文章标题长度不能超过255个字符")
    public String getTitle()
    {
        return title;
    }

    public void setTitle(String title)
    {
        this.title = title;
    }

    public String getFilePath()
    {
        return filePath;
    }

    public void setFilePath(String filePath)
    {
        this.filePath = filePath;
    }

    public String getGitSha()
    {
        return gitSha;
    }

    public void setGitSha(String gitSha)
    {
        this.gitSha = gitSha;
    }

    public Date getPublishDate()
    {
        return publishDate;
    }

    public void setPublishDate(Date publishDate)
    {
        this.publishDate = publishDate;
    }

    public String getCategories()
    {
        return categories;
    }

    public void setCategories(String categories)
    {
        this.categories = categories;
    }

    public String getTags()
    {
        return tags;
    }

    public void setTags(String tags)
    {
        this.tags = tags;
    }

    public String getCover()
    {
        return cover;
    }

    public void setCover(String cover)
    {
        this.cover = cover;
    }

    public String getRecommend()
    {
        return recommend;
    }

    public void setRecommend(String recommend)
    {
        this.recommend = recommend;
    }

    public String getHide()
    {
        return hide;
    }

    public void setHide(String hide)
    {
        this.hide = hide;
    }

    public String getIsTop()
    {
        return isTop;
    }

    public void setIsTop(String isTop)
    {
        this.isTop = isTop;
    }

    public String getSummary()
    {
        return summary;
    }

    public void setSummary(String summary)
    {
        this.summary = summary;
    }

    public Integer getWordCount()
    {
        return wordCount;
    }

    public void setWordCount(Integer wordCount)
    {
        this.wordCount = wordCount;
    }

    public Date getLastSyncTime()
    {
        return lastSyncTime;
    }

    public void setLastSyncTime(Date lastSyncTime)
    {
        this.lastSyncTime = lastSyncTime;
    }

    public String getContent()
    {
        return content;
    }

    public void setContent(String content)
    {
        this.content = content;
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
                .append("articleId", getArticleId())
                .append("slug", getSlug())
                .append("title", getTitle())
                .append("filePath", getFilePath())
                .append("gitSha", getGitSha())
                .append("publishDate", getPublishDate())
                .append("categories", getCategories())
                .append("tags", getTags())
                .append("cover", getCover())
                .append("recommend", getRecommend())
                .append("hide", getHide())
                .append("isTop", getIsTop())
                .append("summary", getSummary())
                .append("wordCount", getWordCount())
                .append("lastSyncTime", getLastSyncTime())
                .append("createBy", getCreateBy())
                .append("createTime", getCreateTime())
                .append("updateBy", getUpdateBy())
                .append("updateTime", getUpdateTime())
                .append("remark", getRemark())
                .toString();
    }
}
