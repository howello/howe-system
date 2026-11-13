package com.howe.blog.tool.provider;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.howe.blog.domain.BlogArticle;
import com.howe.blog.service.IBlogArticleService;
import com.howe.common.tool.model.ToolAccess;
import com.howe.common.tool.model.ToolDefinition;
import com.howe.common.tool.model.ToolInputSchema;
import com.howe.common.tool.spi.BusinessToolProvider;
import com.howe.common.tool.support.ToolArguments;
import com.howe.common.tool.support.ToolResults;
import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 博客文章只读工具。
 *
 * <p>只查询本地索引，不读取 GitHub 正文，也不回写 Git SHA。
 * 本类只依赖 common 工具契约与本模块 Service，不引用 MCP、SDK 或 OAuth 类型。</p>
 */
public final class BlogArticleToolProvider implements BusinessToolProvider {

    /** 工具名。 */
    private static final String TOOL_NAME = "blog_article_list";

    /** 能力分组，由平台映射到 OAuth scope。 */
    private static final String CAPABILITY_GROUP = "blog.read";

    /** 查询字段白名单。 */
    private static final Set<String> FIELDS = Set.of("title", "category", "page", "pageSize");

    private final IBlogArticleService articles;

    /**
     * 构造博客文章工具提供者。
     *
     * @param articles 博客文章服务
     */
    public BlogArticleToolProvider(IBlogArticleService articles) {
        this.articles = articles;
    }

    /**
     * 返回领域标识。
     *
     * @return 领域标识
     */
    @Override
    public String domain() {
        return "blog";
    }

    /**
     * 返回博客文章只读工具。
     *
     * @return 工具定义集合
     */
    @Override
    public Collection<ToolDefinition> definitions() {
        return List.of(articleList());
    }

    /**
     * 构造文章索引分页工具。
     *
     * @return 工具定义
     */
    private ToolDefinition articleList() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("title", Map.of("type", "string", "maxLength", 100));
        properties.put("category", Map.of("type", "string", "maxLength", 100));
        properties.put("page", Map.of("type", "integer", "minimum", 1, "maximum", 10000, "default", 1));
        properties.put("pageSize", Map.of("type", "integer", "minimum", 1, "maximum", 50, "default", 20));
        return new ToolDefinition(TOOL_NAME, "1", "查询博客文章",
                "查询本地博客文章索引，支持标题与分类包含匹配；不读取 GitHub 正文。"
                        + "分页 page 从 1 开始，pageSize 最大 50。",
                CAPABILITY_GROUP,
                new ToolInputSchema("object", properties, List.of(), false, null, null),
                ToolAccess.ofPermissions(Set.of("blog:article:list")),
                new ToolDefinition.ToolAnnotations("查询博客文章", true, false, true, false, true),
                Duration.ofSeconds(10), 65536,
                arguments -> {
                    ToolArguments.rejectUnknown(arguments, FIELDS);
                    ToolArguments.optionalText(arguments, "title", 100);
                    ToolArguments.optionalText(arguments, "category", 100);
                    ToolArguments.optionalInt(arguments, "page", 1, 1, 10000);
                    ToolArguments.optionalInt(arguments, "pageSize", 20, 1, 50);
                },
                (context, arguments) -> {
                    int page = ToolArguments.optionalInt(arguments, "page", 1, 1, 10000);
                    int pageSize = ToolArguments.optionalInt(arguments, "pageSize", 20, 1, 50);
                    BlogArticle query = new BlogArticle();
                    query.setTitle(ToolArguments.optionalText(arguments, "title", 100));
                    query.setCategories(ToolArguments.optionalText(arguments, "category", 100));

                    PageHelper.startPage(page, pageSize);
                    try {
                        List<BlogArticle> rows = articles.selectBlogArticleList(query);
                        PageInfo<BlogArticle> info = new PageInfo<>(rows);
                        List<Map<String, Object>> data = rows.stream().map(BlogArticleToolProvider::project).toList();
                        return ToolResults.page(data, ToolArguments.pageOf(page, pageSize, data.size(), info.getTotal()),
                                info.isHasNextPage(), "共 " + info.getTotal() + " 篇文章");
                    } finally {
                        PageHelper.clearPage();
                    }
                });
    }

    /**
     * 投影为工具 DTO，排除正文、Git SHA、文件路径与创建者信息。
     *
     * @param article 文章索引
     * @return 安全字段
     */
    private static Map<String, Object> project(BlogArticle article) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("articleId", article.getArticleId() == null ? null : String.valueOf(article.getArticleId()));
        row.put("slug", article.getSlug());
        row.put("title", article.getTitle());
        row.put("categories", split(article.getCategories()));
        row.put("tags", split(article.getTags()));
        row.put("publishDate", article.getPublishDate());
        row.put("wordCount", article.getWordCount());
        row.put("recommend", "1".equals(article.getRecommend()));
        row.put("hide", "1".equals(article.getHide()));
        row.put("isTop", "1".equals(article.getIsTop()));
        return row;
    }

    /**
     * 将逗号分隔的分类/标签转为数组。
     *
     * @param value 逗号分隔文本
     * @return 去空后的数组
     */
    private static List<String> split(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return List.of(value.split(",")).stream().map(String::trim).filter(item -> !item.isEmpty()).toList();
    }
}
