package com.howe.book.service.impl;

import cn.hutool.http.HttpRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.howe.book.service.BookSearchService;
import com.howe.common.exception.ServiceException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 从固定的公共数据源读取检索候选。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookSearchServiceImpl implements BookSearchService {
    private final ObjectMapper json;

    /** 按来源查询电子书或图书条目。 */
    @Override
    public List<Map<String, Object>> catalog(String keyword, String source) {
        String query = encode(keyword);
        var items = new ArrayList<Map<String, Object>>();
        switch (source) {
            case "douban-book" -> {
                for (JsonNode item : fetch(HttpRequest.get("https://book.douban.com/j/subject_suggest?q=" + query),
                        "https://book.douban.com/")) {
                    addBook(items, item, item.path("author_name").asText(), item.path("pic").asText(),
                        item.path("year").asText(), "豆瓣图书", "", "");
                }
            }
            case "douban-read" -> {
                var body = json.createObjectNode();
                body.put("sort", "default").put("page", 1).put("q", keyword.strip()).put("rootKind", "book");
                body.putObject("extensions").putObject("persistedQuery").put("version", 1)
                    .put("sha256Hash", "sha256:92911ffc68782f44a56764094edc0068f283c29fb447277f8ed2911af6bbd0a0");
                body.putObject("variables").put("includeRating", true).put("includeBookRating", true)
                    .put("includeUpdateTime", false).put("includeSaleInfo", false).put("includePurchase", true)
                    .put("includeLibraryCount", false).put("includeFandom", false).put("includeFanfictionTags", false);
                for (JsonNode item : fetch(HttpRequest.post("https://read.douban.com/j/search_v2")
                        .body(body.toString(), "application/json"), "https://read.douban.com/search").path("list")) {
                    var authors = new ArrayList<String>();
                    item.path("author").forEach(author -> authors.add(author.path("name").asText()));
                    JsonNode bookInfo = item.path("book");
                    addBook(items, item, String.join("、", authors), item.path("cover").asText(),
                        "", "豆瓣阅读", item.path("abstract").asText(), item.path("averageRating").asText(),
                        bookInfo.path("publisher").asText(""), "", "", bookInfo.path("isbn13").asText(""),
                        item.path("tags").isArray() ? joinText(item.path("tags")) : "");
                }
            }
            case "weread" -> {
                for (JsonNode group : fetch(HttpRequest.get("https://weread.qq.com/api/store/search?keyword=" + query),
                        "https://weread.qq.com/").path("results")) {
                    if (group.path("type").asInt() != 1) {
                        continue;
                    }
                    for (JsonNode result : group.path("books")) {
                        JsonNode item = result.path("bookInfo");
                        String rating = item.hasNonNull("newRating") ? String.valueOf(item.path("newRating").asDouble() / 100) : "";
                        addBook(items, item, item.path("author").asText(), item.path("cover").asText(),
                            "", "微信读书", "", rating);
                    }
                }
            }
            case "openlibrary" -> {
                // Open Library：有 CORS 且无需鉴权，中文覆盖一般但可作为兜底
                for (JsonNode item : fetch(HttpRequest.get("https://openlibrary.org/search.json?limit=15&fields=title,author_name,first_publish_year,cover_i,publisher,number_of_pages_median,isbn&q=" + query),
                        "https://openlibrary.org/").path("docs")) {
                    int coverId = item.path("cover_i").asInt();
                    addBook(items, item, item.path("author_name").path(0).asText(""),
                        coverId > 0 ? "https://covers.openlibrary.org/b/id/" + coverId + "-M.jpg" : "",
                        item.path("first_publish_year").asText(""), "Open Library", "", "",
                        item.path("publisher").path(0).asText(""), "",
                        item.path("number_of_pages_median").asText(""),
                        item.path("isbn").path(0).asText(""), "");
                }
            }
            case "google-books" -> {
                // Google Books：公开卷册检索；未配置 key 时配额有限，失败由上层提示
                for (JsonNode item : fetch(HttpRequest.get("https://www.googleapis.com/books/v1/volumes?maxResults=15&q=" + query),
                        "https://www.google.com/").path("items")) {
                    JsonNode info = item.path("volumeInfo");
                    var authors = new ArrayList<String>();
                    info.path("authors").forEach(author -> authors.add(author.asText()));
                    JsonNode links = info.path("imageLinks");
                    String cover = links.path("thumbnail").asText(links.path("smallThumbnail").asText(""))
                        .replaceFirst("^http:", "https:");
                    addBook(items, info, String.join("、", authors), cover,
                        info.path("publishedDate").asText(""), "Google Books", info.path("description").asText(""),
                        info.path("averageRating").asText(""),
                        info.path("publisher").asText(""), "",
                        info.path("pageCount").asText(""), "",
                        info.path("categories").isArray() ? joinText(info.path("categories")) : "");
                }
            }
            default -> throw new ServiceException("不支持的书目来源");
        }
        return items;
    }

    /** 规范化三个来源的共同字段。 */
    private void addBook(List<Map<String, Object>> items, JsonNode item, String author, String cover, String year,
            String source, String summary, String rating) {
        addBook(items, item, author, cover, year, source, summary, rating, "", "", "", "", "");
    }

    /**
     * 规范化书目候选。
     *
     * <p>各来源能给的字段不同：豆瓣图书没有简介与 ISBN，Open Library 没有出版社，
     * Google Books 字段最全。这里统一成同一份结构，缺的字段留空串，
     * 前端「用这本」只回填真正有值的字段。</p>
     */
    private void addBook(List<Map<String, Object>> items, JsonNode item, String author, String cover, String year,
            String source, String summary, String rating, String publisher, String translator,
            String pages, String isbn, String tags) {
        String title = item.path("title").asText();
        if (title.isBlank() || items.size() >= 20) {
            return;
        }
        var row = new java.util.LinkedHashMap<String, Object>();
        row.put("title", title);
        row.put("author", author == null ? "" : author);
        row.put("cover", cover == null ? "" : cover);
        row.put("year", year == null ? "" : year);
        row.put("source", source);
        row.put("summary", summary == null ? "" : summary);
        row.put("rating", rating == null ? "" : rating);
        row.put("publisher", publisher == null ? "" : publisher);
        row.put("translator", translator == null ? "" : translator);
        row.put("pages", pages == null ? "" : pages);
        row.put("isbn", isbn == null ? "" : isbn);
        row.put("tags", tags == null ? "" : tags);
        items.add(row);
    }

    /**
     * 查询配图候选。
     *
     * <p>主源用必应图片的异步列表（返回 HTML 片段，含原图地址与标题），
     * 失败时回退 360 图片；两者都只取明确给出地址的条目，不把任意外链塞给前端。</p>
     */
    @Override
    public List<Map<String, Object>> images(String keyword) {
        return images(keyword, "auto");
    }

    /** 按来源查询配图；auto 时必应优先、空结果回退 360。 */
    @Override
    public List<Map<String, Object>> images(String keyword, String source) {
        String picked = source == null ? "auto" : source.strip().toLowerCase(java.util.Locale.ROOT);
        return switch (picked) {
            case "bing" -> bingImages(keyword);
            case "so360", "so" -> soImages(keyword);
            default -> {
                var items = bingImages(keyword);
                yield items.isEmpty() ? soImages(keyword) : items;
            }
        };
    }

    /** 必应图片：从 HTML 片段里取 murl（原图）与 t（标题）。 */
    private List<Map<String, Object>> bingImages(String keyword) {
        var items = new ArrayList<Map<String, Object>>();
        String endpoint = "https://www.bing.com/images/async?q=" + encode(keyword)
            + "&first=0&count=24&mmasync=1";
        try {
            String html = fetchText(HttpRequest.get(endpoint), "https://www.bing.com/images/");
            var matcher = java.util.regex.Pattern
                .compile("murl&quot;:&quot;(.*?)&quot;.*?t&quot;:&quot;(.*?)&quot;").matcher(html);
            while (matcher.find() && items.size() < 20) {
                String url = matcher.group(1);
                if (url.startsWith("https://")) {
                    items.add(Map.of("title", matcher.group(2), "cover", url, "source", "必应图片"));
                }
            }
        } catch (Exception exception) {
            log.warn("bing images failed: {}", exception.toString(), exception);
        }
        return items;
    }

    /**
     * 360 图片：JSON 列表，取 thumb（比原图更适合网格预览）。
     *
     * <p>该接口对单个词一次返回很多、对多词组合往往只给 2 条，
     * 因此按需翻页合并，直到攒够目标数量或没有更多结果。</p>
     */
    private List<Map<String, Object>> soImages(String keyword) {
        var items = new ArrayList<Map<String, Object>>();
        var seen = new java.util.HashSet<String>();
        for (int pn = 0; pn < 120 && items.size() < 24; pn += 30) {
            JsonNode list;
            try {
                list = fetch(HttpRequest.get("https://image.so.com/j?rn=30&pn=" + pn + "&q=" + encode(keyword)),
                    "https://image.so.com/").path("list");
            } catch (Exception exception) {
                log.warn("so images failed at pn={}: {}", pn, exception.toString());
                break;
            }
            for (JsonNode item : list) {
                String url = item.path("thumb").asText();
                if (url.startsWith("https://") && seen.add(url)) {
                    // 360 的 title 是来源网页标题，过长且带站点名；只保留前半段作为配图名
                    String title = item.path("title").asText(keyword).replaceAll("\s*[-_|].*$", "").strip();
                    if (title.length() > 18) {
                        title = title.substring(0, 18);
                    }
                    items.add(Map.of("title", title.isBlank() ? keyword : title, "cover", url, "source", "360图片"));
                }
            }
        }
        return items;
    }

    /** 请求固定上游地址并返回文本正文，用于 HTML 片段。 */
    private String fetchText(HttpRequest request, String referer) {
        try (var response = request.header("Referer", referer)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Safari/537.36")
                // 必应缺这两个头会返回 200 + 空体
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "zh-CN,zh;q=0.9")
                .timeout(8000).setFollowRedirects(true).execute()) {
            if (response.isOk()) {
                return response.body();
            }
        }
        throw new ServiceException("检索服务暂不可用，请稍后重试");
    }

    /** 把字符串数组拼成顿号分隔的文本。 */
    private String joinText(JsonNode array) {
        var values = new ArrayList<String>();
        array.forEach(node -> {
            String text = node.isTextual() ? node.asText() : node.path("name").asText("");
            if (!text.isBlank()) {
                values.add(text);
            }
        });
        return String.join("、", values);
    }

    /** 检查检索词并编码。 */
    private String encode(String query) {
        if (query == null || query.isBlank() || query.length() > 120) {
            throw new ServiceException("请输入 1 至 120 字的关键词");
        }
        // 百度图片对 `+` 表示的空格返回空列表，统一用 %20
        return URLEncoder.encode(query.strip(), StandardCharsets.UTF_8).replace("+", "%20");
    }

    /** 请求固定上游地址，超时或格式变化时返回可重试提示。 */
    private JsonNode fetch(HttpRequest request, String referer) {
        try (var response = request.header("Referer", referer)
                .header("User-Agent", "Mozilla/5.0").timeout(6000).setFollowRedirects(false).execute()) {
            log.info("book search upstream {} -> {}", request.getUrl(), response.getStatus());
            if (response.isOk()) {
                return json.readTree(response.body());
            }
        } catch (Exception exception) {
            log.warn("book search upstream failed {}: {}", request.getUrl(), exception.toString(), exception);
            throw new ServiceException("检索服务暂不可用，请稍后重试");
        }
        throw new ServiceException("检索服务暂不可用，请稍后重试");
    }
}
