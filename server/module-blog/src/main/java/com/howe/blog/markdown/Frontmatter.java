package com.howe.blog.markdown;

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
public class Frontmatter
{
    /** 标题，必填 */
    private String title;

    /** 分类，必填。schema 是单个字符串而非数组 */
    private String categories;

    /** 标签，可选 */
    private List<String> tags = new ArrayList<>();

    /** 文章标识，必填。决定 URL /article/{id} */
    private String id;

    /** 发布日期，必填 */
    private Date date;

    /** 更新日期，可选 */
    private Date updated;

    /** 封面图，可选 */
    private String cover;

    /** 是否推荐，可选 */
    private Boolean recommend;

    /** 是否隐藏，可选 */
    private Boolean hide;

    /** 是否置顶，可选 */
    private Boolean top;

    /**
     * schema 之外的字段原样保留
     *
     * <p>
     * 手写文章里可能有主题未来才支持的字段，编辑保存时不能把它们丢掉。
     * </p>
     */
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
