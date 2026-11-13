package com.howe.blog.tool.provider;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.howe.blog.domain.BlogArticle;
import com.howe.blog.domain.BlogDraft;
import com.howe.blog.domain.BlogTalk;
import com.howe.blog.service.IBlogArticleService;
import com.howe.blog.service.IBlogDraftService;
import com.howe.blog.service.IBlogTalkService;
import com.howe.blog.tool.dto.BlogReadDtos.ArticleView;
import com.howe.blog.tool.dto.BlogReadDtos.DraftDetail;
import com.howe.blog.tool.dto.BlogReadDtos.DraftSummary;
import com.howe.blog.tool.dto.BlogReadDtos.TalkDetail;
import com.howe.blog.tool.dto.BlogReadDtos.TalkSummary;
import com.howe.common.exception.ServiceException;
import com.howe.common.tool.exception.ToolException;
import com.howe.common.tool.model.ToolAccess;
import com.howe.common.tool.model.ToolDefinition;
import com.howe.common.tool.model.ToolInputSchema;
import com.howe.common.tool.model.ToolResult;
import com.howe.common.tool.spi.BusinessToolProvider;
import com.howe.common.tool.support.ToolArguments;
import com.howe.common.tool.support.ToolResults;
import java.time.Duration;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 博客只读工具提供者。
 *
 * <p>只发布 C2 新增的 5 个只读工具；C1 的 {@code blog_article_list} 仍由
 * {@link BlogArticleToolProvider} 注册，本类不重复注册。文章正文读取走
 * 不回写索引的工具专用入口，草稿与说说的正文由 SQL 侧有界截取。</p>
 *
 * <p>本类只依赖 common 工具契约与本模块 Service，不引用 MCP、SDK 或 OAuth 类型。</p>
 */
public final class BlogReadToolProvider implements BusinessToolProvider {

    /** 能力分组，由平台映射到 {@code blog:read}。 */
    private static final String CAPABILITY_GROUP = "blog.read";

    /** 文章详情正文默认上限。 */
    private static final int DEFAULT_CONTENT_CHARS = 20000;

    /** 正文上限的最大值。 */
    private static final int MAX_CONTENT_CHARS = 40000;

    /** 说说列表正文上限。 */
    private static final int TALK_LIST_CONTENT_CHARS = 500;

    /** 说说详情正文上限。 */
    private static final int TALK_DETAIL_CONTENT_CHARS = 40000;

    /** 结果预算：列表。 */
    private static final int LIST_BYTES = 65536;

    /** 结果预算：详情。 */
    private static final int DETAIL_BYTES = 131072;

    /** 分页字段。 */
    private static final Set<String> PAGING_FIELDS = Set.of("page", "pageSize");

    /** 时间输出格式：带偏移量的 ISO-8601。 */
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    /** 文章详情输入字段。 */
    private static final Set<String> ARTICLE_GET_FIELDS = Set.of("articleId", "includeContent", "maxContentChars");

    /** 草稿列表输入字段。 */
    private static final Set<String> DRAFT_LIST_FIELDS = Set.of("title", "status", "page", "pageSize");

    /** 草稿详情输入字段。 */
    private static final Set<String> DRAFT_GET_FIELDS = Set.of("draftId", "maxContentChars");

    /** 说说列表输入字段。 */
    private static final Set<String> TALK_LIST_FIELDS = Set.of("status", "page", "pageSize");

    /** 说说详情输入字段。 */
    private static final Set<String> TALK_GET_FIELDS = Set.of("talkId");

    private final IBlogArticleService articles;

    private final IBlogDraftService drafts;

    private final IBlogTalkService talks;

    /**
     * 构造博客只读工具提供者。
     *
     * @param articles 文章服务
     * @param drafts 草稿服务
     * @param talks 说说服务
     */
    public BlogReadToolProvider(IBlogArticleService articles, IBlogDraftService drafts, IBlogTalkService talks) {
        this.articles = Objects.requireNonNull(articles);
        this.drafts = Objects.requireNonNull(drafts);
        this.talks = Objects.requireNonNull(talks);
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
     * 返回 C2 新增的 5 个博客只读工具。
     *
     * @return 工具定义集合
     */
    @Override
    public Collection<ToolDefinition> definitions() {
        return List.of(articleGet(), draftList(), draftGet(), talkList(), talkGet());
    }

    /**
     * 文章详情：可选读取 GitHub 正文，不回写本地索引。
     *
     * @return 工具定义
     */
    private ToolDefinition articleGet() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("articleId", Map.of("type", "string", "pattern", "^[1-9][0-9]{0,18}$"));
        properties.put("includeContent", Map.of("type", "boolean", "default", true));
        properties.put("maxContentChars",
                Map.of("type", "integer", "minimum", 1, "maximum", MAX_CONTENT_CHARS, "default",
                        DEFAULT_CONTENT_CHARS));
        return new ToolDefinition("blog_article_get", "1", "读取博客文章详情",
                "读取单篇文章的安全字段；includeContent=true 时从固定 GitHub 仓库读取正文，"
                        + "不回写本地索引。正文超过服务端上限时返回 result_too_large。",
                CAPABILITY_GROUP,
                new ToolInputSchema("object", properties, List.of("articleId"), false, null, null),
                ToolAccess.ofPermissions(Set.of("blog:article:query")),
                new ToolDefinition.ToolAnnotations("读取博客文章详情", true, false, true, true, true),
                Duration.ofSeconds(20), DETAIL_BYTES,
                arguments -> {
                    ToolArguments.rejectUnknown(arguments, ARTICLE_GET_FIELDS);
                    ToolArguments.requiredId(arguments, "articleId");
                    ToolArguments.optionalBoolean(arguments, "includeContent", true);
                    ToolArguments.optionalInt(arguments, "maxContentChars", DEFAULT_CONTENT_CHARS, 1,
                            MAX_CONTENT_CHARS);
                },
                (context, arguments) -> {
                    Long articleId = ToolArguments.requiredId(arguments, "articleId");
                    boolean includeContent = ToolArguments.optionalBoolean(arguments, "includeContent", true);
                    int maxChars = ToolArguments.optionalInt(arguments, "maxContentChars", DEFAULT_CONTENT_CHARS, 1,
                            MAX_CONTENT_CHARS);
                    BlogArticle article = read(() -> articles.readArticleForTool(articleId, includeContent, maxChars),
                            "文章");
                    Truncated content = truncate(article.getContent(), maxChars);
                    List<Map<String, Object>> warnings = content.truncated()
                            ? List.of(Map.of("code", "CONTENT_TRUNCATED", "message", "正文已按上限截断")) : List.of();
                    ArticleView view = new ArticleView(String.valueOf(article.getArticleId()), article.getSlug(),
                            article.getTitle(), split(article.getCategories()), split(article.getTags()),
                            time(article.getPublishDate()), article.getWordCount(), flag(article.getRecommend()),
                            flag(article.getHide()), flag(article.getIsTop()), article.getGitSha(),
                            includeContent ? content.text() : null, includeContent && content.truncated());
                    return ToolResults.success(view, null, false, warnings, "已返回文章详情");
                });
    }

    /**
     * 草稿列表：编辑资源范围，不限本人。
     *
     * @return 工具定义
     */
    private ToolDefinition draftList() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("title", Map.of("type", "string", "maxLength", 100));
        properties.put("status", Map.of("type", "string", "enum", List.of("unpublished", "published")));
        paging(properties);
        return new ToolDefinition("blog_draft_list", "1", "查询博客草稿列表",
                "查询当前账号有权管理的全部草稿（编辑资源范围，不是仅本人），返回不含正文的索引字段。",
                CAPABILITY_GROUP,
                new ToolInputSchema("object", properties, List.of(), false, null, null),
                ToolAccess.ofPermissions(Set.of("blog:draft:list")),
                new ToolDefinition.ToolAnnotations("查询博客草稿列表", true, false, true, false, true),
                Duration.ofSeconds(10), LIST_BYTES,
                arguments -> {
                    ToolArguments.rejectUnknown(arguments, DRAFT_LIST_FIELDS);
                    ToolArguments.optionalText(arguments, "title", 100);
                    draftStatus(arguments);
                    ToolArguments.optionalInt(arguments, "page", 1, 1, 10000);
                    ToolArguments.optionalInt(arguments, "pageSize", 20, 1, 50);
                },
                (context, arguments) -> {
                    int page = ToolArguments.optionalInt(arguments, "page", 1, 1, 10000);
                    int pageSize = ToolArguments.optionalInt(arguments, "pageSize", 20, 1, 50);
                    BlogDraft query = new BlogDraft();
                    query.setTitle(ToolArguments.optionalText(arguments, "title", 100));
                    query.setStatus(draftStatus(arguments));

                    PageHelper.startPage(page, pageSize);
                    try {
                        List<BlogDraft> rows = drafts.selectBlogDraftList(query);
                        PageInfo<BlogDraft> info = new PageInfo<>(rows);
                        List<DraftSummary> data = rows.stream()
                                .map(row -> new DraftSummary(String.valueOf(row.getDraftId()), row.getTitle(),
                                        row.getSlug(), draftStatusOf(row.getStatus())))
                                .toList();
                        return ToolResults.page(data, ToolArguments.pageOf(page, pageSize, data.size(), info.getTotal()),
                                info.isHasNextPage(), "共 " + info.getTotal() + " 篇草稿");
                    } finally {
                        PageHelper.clearPage();
                    }
                });
    }

    /**
     * 草稿详情：正文由 SQL 侧有界截取。
     *
     * @return 工具定义
     */
    private ToolDefinition draftGet() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("draftId", Map.of("type", "string", "pattern", "^[1-9][0-9]{0,18}$"));
        properties.put("maxContentChars",
                Map.of("type", "integer", "minimum", 1, "maximum", MAX_CONTENT_CHARS, "default",
                        DEFAULT_CONTENT_CHARS));
        return new ToolDefinition("blog_draft_get", "1", "读取博客草稿详情",
                "读取单篇草稿的标题、状态、分类、标签与有界正文；正文超过上限时按 Unicode 码点截断。",
                CAPABILITY_GROUP,
                new ToolInputSchema("object", properties, List.of("draftId"), false, null, null),
                ToolAccess.ofPermissions(Set.of("blog:draft:query")),
                new ToolDefinition.ToolAnnotations("读取博客草稿详情", true, false, true, false, true),
                Duration.ofSeconds(10), DETAIL_BYTES,
                arguments -> {
                    ToolArguments.rejectUnknown(arguments, DRAFT_GET_FIELDS);
                    ToolArguments.requiredId(arguments, "draftId");
                    ToolArguments.optionalInt(arguments, "maxContentChars", DEFAULT_CONTENT_CHARS, 1,
                            MAX_CONTENT_CHARS);
                },
                (context, arguments) -> {
                    Long draftId = ToolArguments.requiredId(arguments, "draftId");
                    int maxChars = ToolArguments.optionalInt(arguments, "maxContentChars", DEFAULT_CONTENT_CHARS, 1,
                            MAX_CONTENT_CHARS);
                    BlogDraft draft = read(() -> drafts.readDraftForTool(draftId, maxChars), "草稿");
                    if (draft == null) {
                        throw new ToolException("not_found_or_forbidden", "草稿不存在或无权访问");
                    }
                    Truncated content = truncate(draft.getContent(), maxChars);
                    List<Map<String, Object>> warnings = content.truncated()
                            ? List.of(Map.of("code", "CONTENT_TRUNCATED", "message", "正文已按上限截断")) : List.of();
                    DraftDetail view = new DraftDetail(String.valueOf(draft.getDraftId()), draft.getTitle(),
                            draft.getSlug(), draftStatusOf(draft.getStatus()), content.text(), content.truncated(),
                            split(draft.getCategories()), split(draft.getTags()));
                    return ToolResults.success(view, null, false, warnings, "已返回草稿详情");
                });
    }

    /**
     * 说说列表：编辑资源范围，正文按 500 码点截断。
     *
     * @return 工具定义
     */
    private ToolDefinition talkList() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("status", Map.of("type", "string", "enum", List.of("published", "hidden")));
        paging(properties);
        return new ToolDefinition("blog_talk_list", "1", "查询博客说说列表",
                "查询当前账号有权管理的说说（编辑资源范围），正文只返回最多 " + TALK_LIST_CONTENT_CHARS
                        + " 个字符的摘要。",
                CAPABILITY_GROUP,
                new ToolInputSchema("object", properties, List.of(), false, null, null),
                ToolAccess.ofPermissions(Set.of("blog:talk:list")),
                new ToolDefinition.ToolAnnotations("查询博客说说列表", true, false, true, false, true),
                Duration.ofSeconds(10), LIST_BYTES,
                arguments -> {
                    ToolArguments.rejectUnknown(arguments, TALK_LIST_FIELDS);
                    talkStatus(arguments);
                    ToolArguments.optionalInt(arguments, "page", 1, 1, 10000);
                    ToolArguments.optionalInt(arguments, "pageSize", 20, 1, 50);
                },
                (context, arguments) -> {
                    int page = ToolArguments.optionalInt(arguments, "page", 1, 1, 10000);
                    int pageSize = ToolArguments.optionalInt(arguments, "pageSize", 20, 1, 50);
                    BlogTalk query = new BlogTalk();
                    query.setStatus(talkStatus(arguments));

                    PageHelper.startPage(page, pageSize);
                    try {
                        List<BlogTalk> rows = talks.selectBlogTalkListForTool(query, TALK_LIST_CONTENT_CHARS);
                        PageInfo<BlogTalk> info = new PageInfo<>(rows);
                        List<Map<String, Object>> warnings = new ArrayList<>();
                        List<TalkSummary> data = rows.stream().map(row -> {
                            Truncated content = truncate(row.getContent(), TALK_LIST_CONTENT_CHARS);
                            if (content.truncated()) {
                                warnings.add(Map.of("code", "CONTENT_TRUNCATED",
                                        "message", "部分说说正文已按列表上限截断"));
                            }
                            return new TalkSummary(String.valueOf(row.getTalkId()), content.text(),
                                    split(row.getTags()), time(row.getPubDate()), flag(row.getIsTop()),
                                    talkStatusOf(row.getStatus()));
                        }).toList();
                        return ToolResults.success(data,
                                ToolArguments.pageOf(page, pageSize, data.size(), info.getTotal()),
                                info.isHasNextPage(), warnings, "共 " + info.getTotal() + " 条说说");
                    } finally {
                        PageHelper.clearPage();
                    }
                });
    }

    /**
     * 说说详情：正文按 40000 码点截断。
     *
     * @return 工具定义
     */
    private ToolDefinition talkGet() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("talkId", Map.of("type", "string", "pattern", "^[1-9][0-9]{0,18}$"));
        return new ToolDefinition("blog_talk_get", "1", "读取博客说说详情",
                "读取单条说说的正文、标签、发布时间、置顶与状态；正文超过上限时按 Unicode 码点截断。",
                CAPABILITY_GROUP,
                new ToolInputSchema("object", properties, List.of("talkId"), false, null, null),
                ToolAccess.ofPermissions(Set.of("blog:talk:query")),
                new ToolDefinition.ToolAnnotations("读取博客说说详情", true, false, true, false, true),
                Duration.ofSeconds(10), DETAIL_BYTES,
                arguments -> {
                    ToolArguments.rejectUnknown(arguments, TALK_GET_FIELDS);
                    ToolArguments.requiredId(arguments, "talkId");
                },
                (context, arguments) -> {
                    Long talkId = ToolArguments.requiredId(arguments, "talkId");
                    BlogTalk talk = read(() -> talks.readBlogTalkForTool(talkId, TALK_DETAIL_CONTENT_CHARS), "说说");
                    if (talk == null) {
                        throw new ToolException("not_found_or_forbidden", "说说不存在或无权访问");
                    }
                    Truncated content = truncate(talk.getContent(), TALK_DETAIL_CONTENT_CHARS);
                    List<Map<String, Object>> warnings = content.truncated()
                            ? List.of(Map.of("code", "CONTENT_TRUNCATED", "message", "正文已按上限截断")) : List.of();
                    TalkDetail view = new TalkDetail(String.valueOf(talk.getTalkId()), content.text(),
                            split(talk.getTags()), time(talk.getPubDate()), flag(talk.getIsTop()),
                            talkStatusOf(talk.getStatus()));
                    return ToolResults.success(view, null, false, warnings, "已返回说说详情");
                });
    }

    /**
     * 在分页属性中加入 page 与 pageSize。
     *
     * @param properties 目标属性表
     */
    private static void paging(Map<String, Object> properties) {
        properties.put("page", Map.of("type", "integer", "minimum", 1, "maximum", 10000, "default", 1));
        properties.put("pageSize", Map.of("type", "integer", "minimum", 1, "maximum", 50, "default", 20));
    }

    /**
     * 读取草稿状态入参并转换为库中值。
     *
     * @param arguments 入参
     * @return 库中状态值；未提供时为 null
     */
    private static String draftStatus(Map<String, Object> arguments) {
        String status = ToolArguments.optionalText(arguments, "status", 16);
        if (status == null) {
            return null;
        }
        return switch (status) {
            case "unpublished" -> "0";
            case "published" -> "1";
            default -> throw new IllegalArgumentException("参数取值不合法：status");
        };
    }

    /**
     * 把库中草稿状态转为对外枚举。
     *
     * @param status 库中状态
     * @return 对外状态
     */
    private static String draftStatusOf(String status) {
        return "1".equals(status) ? "published" : "unpublished";
    }

    /**
     * 读取说说状态入参并转换为库中值。
     *
     * @param arguments 入参
     * @return 库中状态值；未提供时为 null
     */
    private static String talkStatus(Map<String, Object> arguments) {
        String status = ToolArguments.optionalText(arguments, "status", 16);
        if (status == null) {
            return null;
        }
        return switch (status) {
            case "published" -> "0";
            case "hidden" -> "1";
            default -> throw new IllegalArgumentException("参数取值不合法：status");
        };
    }

    /**
     * 把库中说说状态转为对外枚举。
     *
     * @param status 库中状态
     * @return 对外状态
     */
    private static String talkStatusOf(String status) {
        return "1".equals(status) ? "hidden" : "published";
    }

    /**
     * 执行领域读取并把业务异常收敛为稳定工具错误。
     *
     * <p>不把 {@link ServiceException#getMessage()} 原样返回，避免泄漏内部细节。</p>
     *
     * @param action 读取动作
     * @param label 对象名称，仅用于日志语义
     * @param <T> 返回类型
     * @return 读取结果
     */
    private static <T> T read(java.util.function.Supplier<T> action, String label) {
        try {
            return action.get();
        } catch (ToolException exception) {
            throw exception;
        } catch (ServiceException exception) {
            throw new ToolException("not_found_or_forbidden", label + "不存在或无权访问");
        } catch (com.howe.blog.github.GithubReadException exception) {
            throw switch (exception.reason()) {
                case TOO_LARGE -> new ToolException("result_too_large", "文章正文超过服务端读取上限");
                case TIMEOUT -> new ToolException("tool_timeout", "读取文章正文超时", true);
                case UNAVAILABLE -> new ToolException("upstream_unavailable", "读取文章正文失败，请稍后重试", true);
            };
        } catch (RuntimeException exception) {
            throw new ToolException("internal_error", "读取" + label + "失败");
        }
    }

    /**
     * 按 Unicode 码点截断文本，不拆分代理对。
     *
     * @param text 原始文本
     * @param maxCodePoints 最大码点数
     * @return 截断结果
     */
    private static Truncated truncate(String text, int maxCodePoints) {
        if (text == null) {
            return new Truncated(null, false);
        }
        int count = text.codePointCount(0, text.length());
        if (count <= maxCodePoints) {
            return new Truncated(text, false);
        }
        return new Truncated(text.substring(0, text.offsetByCodePoints(0, maxCodePoints)), true);
    }

    /**
     * 截断结果。
     *
     * @param text 截断后的文本
     * @param truncated 是否发生截断
     */
    private record Truncated(String text, boolean truncated) {
    }

    /**
     * 把逗号分隔文本转为数组。
     *
     * @param value 逗号分隔文本
     * @return 去空后的数组
     */
    private static List<String> split(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(value.split(",")).map(String::trim).filter(item -> !item.isEmpty()).toList();
    }

    /**
     * 判断 0/1 标记是否成立。
     *
     * @param value 标记值
     * @return 为 1 时返回 true
     */
    private static boolean flag(String value) {
        return "1".equals(value);
    }

    /**
     * 把时间格式化为带偏移量的 ISO-8601。
     *
     * @param value 时间
     * @return 格式化文本；为空时返回 null
     */
    private static String time(java.util.Date value) {
        if (value == null) {
            return null;
        }
        return TIME_FORMAT.format(value.toInstant().atZone(ZoneId.systemDefault()).toOffsetDateTime());
    }
}
