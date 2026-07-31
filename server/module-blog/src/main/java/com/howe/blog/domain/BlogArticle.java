package com.howe.blog.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.howe.common.annotation.Excel;
import com.howe.common.annotation.Excel.ColumnType;
import com.howe.common.core.domain.BaseEntity;
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
public class BlogArticle extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 文章ID（本地索引主键） */
    @Excel(name = "文章ID", cellType = ColumnType.NUMERIC)
    private Long articleId;

    /** 文章标识，决定 URL /article/{slug} */
    @Excel(name = "文章标识")
    private String slug;

    /** 文章标题 */
    @Excel(name = "标题")
    private String title;

    /** 仓库内文件路径 */
    @Excel(name = "文件路径")
    private String filePath;

    /** 文件 blob sha，GitHub 写操作的乐观锁凭据 */
    private String gitSha;

    /** 发布日期 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "发布日期", dateFormat = "yyyy-MM-dd")
    private Date publishDate;

    /** 分类，多个用逗号分隔 */
    @Excel(name = "分类")
    private String categories;

    /** 标签，多个用逗号分隔 */
    @Excel(name = "标签")
    private String tags;

    /** 封面图地址 */
    private String cover;

    /** 是否推荐（0否 1是） */
    @Excel(name = "推荐", readConverterExp = "0=否,1=是")
    private String recommend;

    /** 是否隐藏（0否 1是） */
    @Excel(name = "隐藏", readConverterExp = "0=否,1=是")
    private String hide;

    /** 是否置顶（0否 1是） */
    @Excel(name = "置顶", readConverterExp = "0=否,1=是")
    private String isTop;

    /** 摘要 */
    private String summary;

    /** 正文字数 */
    @Excel(name = "字数", cellType = ColumnType.NUMERIC)
    private Integer wordCount;

    /** 最后一次与仓库对齐的时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date lastSyncTime;

    /** markdown 正文，不落库：读详情时从 GitHub 拉取，保存时写回 GitHub */
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
