package com.howe.system.domain;

import com.howe.common.utils.StringUtils;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 缓存信息
 *
 * @author howe
 */
@Schema(description = "缓存信息")
public class SysCache
{
    /** 缓存名称 */
    @Schema(description = "缓存名称", example = "sys_config")
    private String cacheName = "";

    /** 缓存键名 */
    @Schema(description = "缓存键名", example = "sys.index.skinName")
    private String cacheKey = "";

    /** 缓存内容 */
    @Schema(description = "缓存内容", example = "skin-blue")
    private String cacheValue = "";

    /** 备注 */
    @Schema(description = "备注", example = "配置信息")
    private String remark = "";

    public SysCache()
    {

    }

    public SysCache(String cacheName, String remark)
    {
        this.cacheName = cacheName;
        this.remark = remark;
    }

    public SysCache(String cacheName, String cacheKey, String cacheValue)
    {
        this.cacheName = StringUtils.replace(cacheName, ":", "");
        this.cacheKey = StringUtils.replace(cacheKey, cacheName, "");
        this.cacheValue = cacheValue;
    }

    public String getCacheName()
    {
        return cacheName;
    }

    public void setCacheName(String cacheName)
    {
        this.cacheName = cacheName;
    }

    public String getCacheKey()
    {
        return cacheKey;
    }

    public void setCacheKey(String cacheKey)
    {
        this.cacheKey = cacheKey;
    }

    public String getCacheValue()
    {
        return cacheValue;
    }

    public void setCacheValue(String cacheValue)
    {
        this.cacheValue = cacheValue;
    }

    public String getRemark()
    {
        return remark;
    }

    public void setRemark(String remark)
    {
        this.remark = remark;
    }
}
