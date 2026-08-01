package com.howe.blog.rss;

import java.util.Date;

/**
 * RSS/Atom 解析后的统一条目
 *
 * <p>ROME 已把 RSS 0.9/1.0/2.0 与 Atom 0.3/1.0 归一成 SyndEntry，
 * 本 record 只是再收敛成本项目真正要用的五个字段。</p>
 *
 * @param title   标题
 * @param author  作者
 * @param url     原文链接，同时是去重键
 * @param summary 摘要纯文本，已剥离 HTML 并截断
 * @param pubDate 发布时间
 * @author howe
 */
public record FeedEntry(String title, String author, String url, String summary, Date pubDate) {
}
