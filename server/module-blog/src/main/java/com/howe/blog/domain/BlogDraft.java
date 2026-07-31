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
 * 博客草稿 blog_draft
 *
 * <p>
 * 纯本地表：草稿不进 GitHub，只有点了「发布」才会生成 markdown 提交到仓库。
 * </p>
 *
 * @author howe
 */
@Schema(description = "博客草稿，纯本地表，草稿不进 GitHub 仓库，点发布才生成 markdown 提交")
public class BlogDraft extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 草稿ID */
    @Schema(description = "草稿ID", example = "1")
    @Excel(name = "草稿ID", cellType = ColumnType.NUMERIC)
    private Long draftId;

    /** 标题 */
    @Schema(description = "标题", example = "JVM 原理｜java知识点")
    @Excel(name = "标题")
    private String title;

    /** 计划使用的文章标识，发布时作为 frontmatter 的 id */
    @Schema(description = "计划使用的文章标识，发布时作为 frontmatter 的 id，决定文章 URL", example = "interview-notes-jvm")
    @Excel(name = "文章标识")
    private String slug;

    /** markdown 正文，不含 frontmatter */
    @Schema(description = "markdown 正文，不含 frontmatter")
    private String content;

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

    /** 计划发布日期 */
    @Schema(description = "计划发布日期", example = "2026-03-30 11:26:55")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "计划发布日期", dateFormat = "yyyy-MM-dd")
    private Date publishDate;

    /** 是否推荐（0否 1是） */
    @Schema(description = "是否推荐（0否 1是）", example = "0")
    private String recommend;

    /** 是否隐藏（0否 1是） */
    @Schema(description = "是否隐藏（0否 1是），只是从首页/RSS 隐去，不等于草稿", example = "0")
    private String hide;

    /** 是否置顶（0否 1是） */
    @Schema(description = "是否置顶（0否 1是）", example = "0")
    private String isTop;

    /** 状态（0草稿 1已发布） */
    @Schema(description = "状态（0草稿 1已发布）", example = "0")
    @Excel(name = "状态", readConverterExp = "0=草稿,1=已发布")
    private String status;

    /** 发布后对应的仓库文件路径 */
    @Schema(description = "发布后对应的仓库文件路径", example = "src/content/blog/interview-notes/01-jvm.md")
    private String publishedPath;

    /** 发布时间 */
    @Schema(description = "发布时间", example = "2026-07-30 09:00:00")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date publishTime;

    public Long getDraftId()
    {
        return draftId;
    }

    public void setDraftId(Long draftId)
    {
        this.draftId = draftId;
    }

    @NotBlank(message = "标题不能为空")
    @Size(min = 0, max = 255, message = "标题长度不能超过255个字符")
    public String getTitle()
    {
        return title;
    }

    public void setTitle(String title)
    {
        this.title = title;
    }

    @Size(min = 0, max = 128, message = "文章标识长度不能超过128个字符")
    @Pattern(regexp = "^$|^[A-Za-z0-9][A-Za-z0-9._-]*$", message = "文章标识只能包含字母、数字、点、下划线和中划线，且以字母或数字开头")
    public String getSlug()
    {
        return slug;
    }

    public void setSlug(String slug)
    {
        this.slug = slug;
    }

    public String getContent()
    {
        return content;
    }

    public void setContent(String content)
    {
        this.content = content;
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

    public Date getPublishDate()
    {
        return publishDate;
    }

    public void setPublishDate(Date publishDate)
    {
        this.publishDate = publishDate;
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

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getPublishedPath()
    {
        return publishedPath;
    }

    public void setPublishedPath(String publishedPath)
    {
        this.publishedPath = publishedPath;
    }

    public Date getPublishTime()
    {
        return publishTime;
    }

    public void setPublishTime(Date publishTime)
    {
        this.publishTime = publishTime;
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
                .append("draftId", getDraftId())
                .append("title", getTitle())
                .append("slug", getSlug())
                .append("categories", getCategories())
                .append("tags", getTags())
                .append("cover", getCover())
                .append("publishDate", getPublishDate())
                .append("recommend", getRecommend())
                .append("hide", getHide())
                .append("isTop", getIsTop())
                .append("status", getStatus())
                .append("publishedPath", getPublishedPath())
                .append("publishTime", getPublishTime())
                .append("createBy", getCreateBy())
                .append("createTime", getCreateTime())
                .append("updateBy", getUpdateBy())
                .append("updateTime", getUpdateTime())
                .append("remark", getRemark())
                .toString();
    }
}
