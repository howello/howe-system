package com.howe.system.domain.vo;

import com.howe.common.utils.StringUtils;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 路由显示信息
 *
 * @author howe
 */
@Schema(description = "路由显示信息")
public class MetaVo
{
    /**
     * 设置该路由在侧边栏和面包屑中展示的名字
     */
    @Schema(description = "该路由在侧边栏和面包屑中展示的名字", example = "用户管理")
    private String title;

    /**
     * 设置该路由的图标，对应路径src/assets/icons/svg
     */
    @Schema(description = "该路由的图标，对应路径 src/assets/icons/svg", example = "user")
    private String icon;

    /**
     * 设置为true，则不会被 <keep-alive>缓存
     */
    @Schema(description = "设置为 true 则不会被 keep-alive 缓存", example = "false")
    private boolean noCache;

    /**
     * 内链地址（http(s)://开头）
     */
    @Schema(description = "内链地址（http(s):// 开头）", example = "https://howe.vip")
    private String link;

    public MetaVo()
    {
    }

    public MetaVo(String title, String icon)
    {
        this.title = title;
        this.icon = icon;
    }

    public MetaVo(String title, String icon, boolean noCache)
    {
        this.title = title;
        this.icon = icon;
        this.noCache = noCache;
    }

    public MetaVo(String title, String icon, String link)
    {
        this.title = title;
        this.icon = icon;
        this.link = link;
    }

    public MetaVo(String title, String icon, boolean noCache, String link)
    {
        this.title = title;
        this.icon = icon;
        this.noCache = noCache;
        if (StringUtils.ishttp(link))
        {
            this.link = link;
        }
    }

    public boolean isNoCache()
    {
        return noCache;
    }

    public void setNoCache(boolean noCache)
    {
        this.noCache = noCache;
    }

    public String getTitle()
    {
        return title;
    }

    public void setTitle(String title)
    {
        this.title = title;
    }

    public String getIcon()
    {
        return icon;
    }

    public void setIcon(String icon)
    {
        this.icon = icon;
    }

    public String getLink()
    {
        return link;
    }

    public void setLink(String link)
    {
        this.link = link;
    }
}
