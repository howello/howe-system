package com.howe.blog.rss;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HtmlUtil;
import com.howe.blog.util.UrlGuard;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.FeedException;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;

/**
 * RSS/Atom 解析器
 *
 * <p>ROME 已把 RSS 0.9/1.0/2.0 与 Atom 0.3/1.0 统一成 SyndFeed/SyndEntry，
 * 因此这里<b>不需要按格式分支</b>。</p>
 *
 * <p>时区：{@code getPublishedDate()} 返回的是绝对时间点，全链路不做任何手工时区转换。
 * 容器 {@code TZ=Asia/Shanghai} 与 {@code DB_URL} 的 {@code serverTimezone} 已保证一致，
 * 对无时区日期统一加 8 小时反而会把本来正确的带时区日期改错。</p>
 *
 * @author howe
 */
@Slf4j
@Component
public class RssParser {

    /** 与 blog_feed_item 的列宽对齐，超长直接截断而不是让 SQL 报错 */
    private static final int MAX_TITLE = 500;
    private static final int MAX_AUTHOR = 128;
    private static final int MAX_URL = 500;
    private static final int MAX_SUMMARY = 500;

    /** 结尾处没有闭合 {@code >} 的悬空标签片段，见 cleanSummary 的说明 */
    private static final Pattern DANGLING_TAG = Pattern.compile("<[/!a-zA-Z][^>]*$");

    /**
     * 解析订阅源字节流
     *
     * @param bytes          原始字节，编码由 XmlReader 从 XML 声明嗅探
     * @param fallbackAuthor 条目与频道都没有作者时的兜底（通常用订阅源名称）
     * @param summaryLength  摘要截断字数
     * @return 条目集合
     * @throws FeedException 格式不是合法的 RSS/Atom
     * @throws IOException   读取失败
     */
    public List<FeedEntry> parse(byte[] bytes, String fallbackAuthor, int summaryLength)
            throws FeedException, IOException {
        SyndFeedInput input = new SyndFeedInput();
        // 抓的是不受控的第三方 XML，allowDoctypes 保持默认的 false —— 开启即引入 XXE 攻击面
        SyndFeed feed;
        try (XmlReader reader = new XmlReader(new ByteArrayInputStream(bytes))) {
            feed = input.build(reader);
        }

        List<FeedEntry> result = new ArrayList<>();
        for (SyndEntry entry : feed.getEntries()) {
            String title = StrUtil.trimToEmpty(entry.getTitle());
            String url = StrUtil.trimToEmpty(entry.getLink());
            // 无标题或无链接的条目没有展示价值，且缺少去重键，直接丢弃
            if (StrUtil.isBlank(title) || StrUtil.isBlank(url)) {
                continue;
            }
            // 条目链接最终会被拼进站点与管理端的 href，必须挡住 javascript: 这类伪协议。
            // HTML 转义只能防属性闭合突破，href="javascript:..." 本身就是合法属性值——
            // 一个被订阅的第三方博客在 <link> 里塞脚本，点一下就在我们的域里执行了。
            if (!UrlGuard.isDisplayableUrl(url)) {
                log.warn("条目[{}]的链接协议不被允许，已丢弃该条", StrUtil.maxLength(url, 120));
                continue;
            }
            String author = firstNonBlank(entry.getAuthor(), feed.getAuthor(), fallbackAuthor);
            Date pubDate = entry.getPublishedDate() != null ? entry.getPublishedDate() : entry.getUpdatedDate();
            if (pubDate == null) {
                log.warn("条目[{}]缺少发布时间，回落为入库时间", url);
                pubDate = new Date();
            }
            result.add(new FeedEntry(
                    truncate(title, MAX_TITLE),
                    truncate(author, MAX_AUTHOR),
                    truncate(url, MAX_URL),
                    truncate(cleanSummary(entry, summaryLength), MAX_SUMMARY),
                    pubDate));
        }
        return result;
    }

    /**
     * 摘要清洗管线，顺序不可颠倒
     *
     * @param entry     条目
     * @param maxLength 截断字数
     * @return 纯文本摘要
     */
    private String cleanSummary(SyndEntry entry, int maxLength) {
        String raw = entry.getDescription() != null ? entry.getDescription().getValue() : null;
        if (StrUtil.isBlank(raw) && CollUtil.isNotEmpty(entry.getContents())) {
            raw = entry.getContents().get(0).getValue();
        }
        if (StrUtil.isBlank(raw)) {
            return "";
        }
        // 必须先 unescape 再剥离标签：反过来的话 &lt;script&gt; 会作为纯文本活下来，
        // 等前端 unescape 时就变回了可执行的标签
        String text = HtmlUtil.unescape(raw);
        text = HtmlUtil.cleanHtmlTag(text);
        // cleanHtmlTag 只认成对的 <...>。源站的摘要常常是从正文剥标签后按字数硬截的，
        // 结尾会剩下「<script src="https://cdn.bo」这种没有闭合 > 的悬空片段，
        // unescape 之后就暴露出来。按 HTML 的 tag-open 规则（< 紧跟 / ! 或字母）把它去掉；
        // 「a < b」这类 < 后面跟空格的正常文本不匹配，不会被误删。
        text = DANGLING_TAG.matcher(text).replaceAll("");
        text = text.replaceAll("\\s+", " ").trim();
        return StrUtil.maxLength(text, maxLength);
    }

    /**
     * 取第一个非空白值
     *
     * @param values 候选值
     * @return 第一个非空白值，全为空时返回空串
     */
    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StrUtil.isNotBlank(value)) {
                return value.trim();
            }
        }
        return "";
    }

    /**
     * 按列宽截断
     *
     * @param text      文本
     * @param maxLength 上限
     * @return 截断后的文本
     */
    private String truncate(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        return text.length() > maxLength ? text.substring(0, maxLength) : text;
    }
}
