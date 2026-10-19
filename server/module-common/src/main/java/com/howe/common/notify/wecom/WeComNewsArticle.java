package com.howe.common.notify.wecom;

import com.alibaba.fastjson2.JSONObject;

/**
 * 企业微信 news 消息中的单篇文章
 *
 * <p>标题最多 128 字节、描述最多 512 字节；链接必填，封面图可选。</p>
 *
 * @author howe
 */
public class WeComNewsArticle {

    /** 标题上限（字节） */
    public static final int MAX_TITLE_BYTES = 128;

    /** 描述上限（字节） */
    public static final int MAX_DESCRIPTION_BYTES = 512;

    /** 标题 */
    private final String title;

    /** 描述 */
    private final String description;

    /** 点击后打开的链接 */
    private final String url;

    /** 封面图链接，JPG/PNG */
    private final String picUrl;

    /**
     * 构造图文文章
     *
     * @param title       标题
     * @param description 描述，可为空
     * @param url         跳转链接
     * @param picUrl      封面图链接，可为空
     */
    public WeComNewsArticle(String title, String description, String url, String picUrl) {
        this.title = title;
        this.description = description;
        this.url = url;
        this.picUrl = picUrl;
    }

    /**
     * 转换为请求体中的单篇文章对象
     *
     * @return JSON 对象
     */
    public JSONObject toJson() {
        JSONObject item = new JSONObject();
        item.put("title", WeComTextUtils.truncateUtf8(title, MAX_TITLE_BYTES));
        if (description != null) {
            item.put("description", WeComTextUtils.truncateUtf8(description, MAX_DESCRIPTION_BYTES));
        }
        item.put("url", url);
        if (picUrl != null && !picUrl.isEmpty()) {
            item.put("picurl", picUrl);
        }
        return item;
    }

    /**
     * 校验文章必填字段
     *
     * @return 校验不通过时的可读原因；通过时返回 null
     */
    public String validate() {
        if (WeComTextUtils.isBlank(title)) {
            return "图文标题不能为空";
        }
        if (WeComTextUtils.isBlank(url)) {
            return "图文链接不能为空";
        }
        return null;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getUrl() {
        return url;
    }

    public String getPicUrl() {
        return picUrl;
    }
}
