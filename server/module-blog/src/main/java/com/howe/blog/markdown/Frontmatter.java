package com.howe.blog.markdown;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.*;

/**
 * 文章 frontmatter
 *
 * <p>
 * 字段与 blog-ui 的 {@code src/content.config.ts} 中 zod schema 一一对应：
 * title / date / categories 必填，其余可选。categories 在 schema 里是单个字符串
 * （{@code z.string()}）而不是数组，tags 才是数组。
 * </p>
 *
 * @author howe
 */
@Schema(description = "文章 frontmatter，字段与 blog-ui 的 content.config.ts 中 zod schema 一一对应")
public class Frontmatter
{
    /** 标题，必填 */
    @Schema(description = "标题，必填", example = "JVM 原理｜java知识点")
    private String title;

    /** 分类，必填。schema 是单个字符串而非数组 */
    @Schema(description = "分类，必填。schema 里是单个字符串而非数组", example = "java知识点")
    private String categories;

    /** 标签，可选 */
    @Schema(description = "标签，可选，写成块状列表", example = "[\"java知识点\", \"Java\"]")
    private List<String> tags = new ArrayList<>();

    /** 文章标识，必填。决定 URL /article/{id} */
    @Schema(description = "文章标识，必填。决定文章 URL /article/{id}，须全局唯一", example = "interview-notes-jvm")
    private String id;

    /** 发布日期，必填 */
    @Schema(description = "发布日期，必填", example = "2026-03-30 11:26:55")
    private Date date;

    /** 更新日期，可选 */
    @Schema(description = "更新日期，可选", example = "2026-07-30")
    private Date updated;

    /** 封面图，可选 */
    @Schema(description = "封面图，可选", example = "https://img.wyantao.com/cover.png")
    private String cover;

    /** 是否推荐，可选 */
    @Schema(description = "是否推荐，可选。只在为 true 时才写进 frontmatter", example = "true")
    private Boolean recommend;

    /** 是否隐藏，可选 */
    @Schema(description = "是否隐藏，可选。只在为 true 时才写进 frontmatter；仅从首页/RSS/上下篇隐去，不等于草稿", example = "true")
    private Boolean hide;

    /** 是否置顶，可选 */
    @Schema(description = "是否置顶，可选。只在为 true 时才写进 frontmatter", example = "true")
    private Boolean top;

    /**
     * schema 之外的字段原样保留
     *
     * <p>
     * 手写文章里可能有主题未来才支持的字段，编辑保存时不能把它们丢掉。
     * </p>
     */
    @Schema(description = "schema 之外的字段原样保留，避免编辑保存时丢掉手写文章里的额外字段")
    private Map<String, String> extras = new LinkedHashMap<>();

    public String getTitle()
    {
        return title;
    }

    public void setTitle(String title)
    {
        this.title = title;
    }

    public String getCategories()
    {
        return categories;
    }

    public void setCategories(String categories)
    {
        this.categories = categories;
    }

    public List<String> getTags()
    {
        return tags;
    }

    public void setTags(List<String> tags)
    {
        this.tags = tags == null ? new ArrayList<>() : tags;
    }

    public String getId()
    {
        return id;
    }

    public void setId(String id)
    {
        this.id = id;
    }

    public Date getDate()
    {
        return date;
    }

    public void setDate(Date date)
    {
        this.date = date;
    }

    public Date getUpdated()
    {
        return updated;
    }

    public void setUpdated(Date updated)
    {
        this.updated = updated;
    }

    public String getCover()
    {
        return cover;
    }

    public void setCover(String cover)
    {
        this.cover = cover;
    }

    public Boolean getRecommend()
    {
        return recommend;
    }

    public void setRecommend(Boolean recommend)
    {
        this.recommend = recommend;
    }

    public Boolean getHide()
    {
        return hide;
    }

    public void setHide(Boolean hide)
    {
        this.hide = hide;
    }

    public Boolean getTop()
    {
        return top;
    }

    public void setTop(Boolean top)
    {
        this.top = top;
    }

    public Map<String, String> getExtras()
    {
        return extras;
    }

    public void setExtras(Map<String, String> extras)
    {
        this.extras = extras == null ? new LinkedHashMap<>() : extras;
    }
}
