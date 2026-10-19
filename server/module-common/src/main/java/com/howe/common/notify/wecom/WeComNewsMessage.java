package com.howe.common.notify.wecom;

import com.alibaba.fastjson2.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 企业微信 news 图文消息
 *
 * <p>一次 1–8 篇；每篇标题最多 128 字节、描述最多 512 字节，链接必填。</p>
 *
 * @author howe
 */
public class WeComNewsMessage implements WeComMessage {

    /** 官方文章数量上限 */
    public static final int MAX_ARTICLES = 8;

    /** 文章列表 */
    private final List<WeComNewsArticle> articles;

    /**
     * 构造 news 消息
     *
     * @param articles 文章列表，1–8 篇
     */
    public WeComNewsMessage(List<WeComNewsArticle> articles) {
        this.articles = articles;
    }

    @Override
    public String msgType() {
        return "news";
    }

    @Override
    public JSONObject payload() {
        List<JSONObject> items = new ArrayList<>();
        if (articles != null) {
            for (WeComNewsArticle article : articles) {
                items.add(article.toJson());
            }
        }
        JSONObject news = new JSONObject();
        news.put("articles", items);
        return news;
    }

    @Override
    public String validate() {
        if (articles == null || articles.isEmpty()) {
            return "图文消息至少需要一篇文章";
        }
        if (articles.size() > MAX_ARTICLES) {
            return "图文消息最多支持 8 篇文章";
        }
        for (WeComNewsArticle article : articles) {
            String error = article.validate();
            if (error != null) {
                return error;
            }
        }
        return null;
    }

    public List<WeComNewsArticle> getArticles() {
        return articles;
    }
}
