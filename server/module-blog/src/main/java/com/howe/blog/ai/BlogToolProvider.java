package com.howe.blog.ai;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.howe.ai.contract.AiToolProvider;
import com.howe.ai.contract.ToolDefinition;
import com.howe.ai.contract.ToolRequest;
import com.howe.ai.contract.ToolResult;
import com.howe.blog.ai.dto.PublicArticleMeta;
import com.howe.blog.ai.dto.PublicBlogStats;
import com.howe.blog.ai.dto.PublicDraftMeta;
import com.howe.blog.domain.BlogArticle;
import com.howe.blog.domain.BlogDraft;
import com.howe.blog.domain.vo.BlogCategoryCount;
import com.howe.blog.domain.vo.BlogHomeStats;
import com.howe.blog.service.IBlogArticleService;
import com.howe.blog.service.IBlogDraftService;
import com.howe.blog.service.IBlogStatsService;
import com.howe.common.constant.ConfigConstants;
import com.howe.common.utils.ConfigUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 博客只读 Tool 的 SPI 实现：把博客公开元数据以只读 Tool 形式暴露给 AI Agent。
 *
 * <p>设计约束（阶段一）：</p>
 * <ul>
 *   <li>只依赖既有 Service，绝不直接引用 Mapper；所有查询经服务层。</li>
 *   <li>输出 DTO 走字段白名单：不返回正文、仓库内文件路径、Git SHA、同步时间或审计字段。</li>
 *   <li>公共文章链接由站点配置 {@link ConfigConstants#BLOG_SITE_URL} + slug 拼出，缺失时回落到空串。</li>
 *   <li>分页类 Tool 钳制 {@link #MAX_PAGE_SIZE} 上限，防止一次性读整张表进堆。</li>
 *   <li>未知 Tool 名直接返回失败，不做猜测式执行。</li>
 * </ul>
 *
 * <p>真正的调用链安全治理（Agent 白名单、用户权限、数据范围、限流、超时、审计）由
 * {@code module-ai} 的 Tool Registry 统一执行，本类只负责查询与 DTO 映射。</p>
 */
@Component
public class BlogToolProvider implements AiToolProvider {

    /** 单次返回的文章/草稿条目上限，防止匿名或失控查询读整张表进堆。 */
    public static final int MAX_PAGE_SIZE = 20;

    /** 标签分布返回的 Top N 上限。 */
    public static final int MAX_TAG_ENTRIES = 20;

    private final IBlogArticleService articleService;
    private final IBlogDraftService draftService;
    private final IBlogStatsService statsService;

    @Autowired
    public BlogToolProvider(IBlogArticleService articleService,
                            IBlogDraftService draftService,
                            IBlogStatsService statsService) {
        this.articleService = articleService;
        this.draftService = draftService;
        this.statsService = statsService;
    }

    @Override
    public List<ToolDefinition> describeTools() {
        List<ToolDefinition> tools = new ArrayList<>(7);
        tools.add(new ToolDefinition("blog_search",
            "按关键词搜索已发布博客文章，返回标题/分类/标签/日期/公共链接等公开元数据（不含正文）",
            "{\"keyword\":\"搜索词\",\"pageSize\":10}"));
        tools.add(new ToolDefinition("blog_article_meta",
            "按 slug 查询单篇已发布文章的公开元数据（不含正文、仓库路径或 Git SHA）",
            "{\"slug\":\"文章标识\"}"));
        tools.add(new ToolDefinition("blog_categories",
            "返回公开分类分布（分类名与文章数），不含内部运维字段",
            "{}"));
        tools.add(new ToolDefinition("blog_tags",
            "返回公开标签分布（标签名与文章数），上限 " + MAX_TAG_ENTRIES + " 项",
            "{}"));
        tools.add(new ToolDefinition("blog_stats",
            "返回博客公开统计：已发布文章总数、草稿总数、分类分布、最近发布文章 slug",
            "{}"));
        tools.add(new ToolDefinition("blog_draft_list",
            "返回草稿列表的公开元数据（标题/标识/分类/标签/状态/计划发布日期，不含正文）",
            "{\"pageSize\":10}"));
        tools.add(new ToolDefinition("blog_draft_meta",
            "按 draftId 查询单篇草稿的公开元数据（不含正文或发布后的仓库路径）",
            "{\"draftId\":1}"));
        return tools;
    }

    @Override
    public ToolResult invoke(ToolRequest request) {
        String tool = request.toolName();
        JSONObject args = parseArgs(request.argumentsJson());
        return switch (tool) {
            case "blog_search" -> search(args);
            case "blog_article_meta" -> articleMeta(args);
            case "blog_categories" -> categories();
            case "blog_tags" -> tags();
            case "blog_stats" -> stats();
            case "blog_draft_list" -> draftList(args);
            case "blog_draft_meta" -> draftMeta(args);
            default -> ToolResult.failure("UNKNOWN_TOOL", "未知的博客 Tool: " + tool);
        };
    }

    // ---- Tool 实现 ----

    private ToolResult search(JSONObject args) {
        int pageSize = clampPageSize(args.getIntValue("pageSize", MAX_PAGE_SIZE));
        String keyword = args.getString("keyword");
        BlogArticle query = new BlogArticle();
        if (keyword != null && !keyword.isBlank()) {
            query.setTitle(keyword.trim());
        }
        List<BlogArticle> all = articleService.selectBlogArticleList(query);
        List<PublicArticleMeta> metas = publicOnly(all).stream()
            .limit(pageSize)
            .map(this::toPublicMeta)
            .toList();
        return ToolResult.success(JSON.toJSONString(metas));
    }

    private ToolResult articleMeta(JSONObject args) {
        String slug = args.getString("slug");
        if (slug == null || slug.isBlank()) {
            return ToolResult.failure("INVALID_ARGUMENT", "slug 不能为空");
        }
        BlogArticle query = new BlogArticle();
        query.setSlug(slug.trim());
        List<BlogArticle> all = articleService.selectBlogArticleList(query);
        BlogArticle hit = publicOnly(all).stream()
            .filter(a -> slug.trim().equals(a.getSlug()))
            .findFirst()
            .orElse(null);
        if (hit == null) {
            return ToolResult.failure("NOT_FOUND", "未找到公开文章: " + slug);
        }
        return ToolResult.success(JSON.toJSONString(toPublicMeta(hit)));
    }

    private ToolResult categories() {
        BlogHomeStats home = statsService.getHomeStats();
        List<PublicBlogStats.CategoryCount> counts = publicCategoryCounts(home);
        return ToolResult.success(JSON.toJSONString(counts));
    }

    private ToolResult tags() {
        // 从已发布文章本地聚合标签分布，钳制 Top N；标签字段为「逗号分隔」
        List<BlogArticle> all = publicOnly(articleService.selectBlogArticleList(new BlogArticle()));
        Map<String, Long> freq = new HashMap<>();
        for (BlogArticle a : all) {
            for (String tag : splitCsv(a.getTags())) {
                freq.merge(tag, 1L, Long::sum);
            }
        }
        List<PublicBlogStats.TagCount> counts = freq.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder())
                .thenComparing(Map.Entry.comparingByKey()))
            .limit(MAX_TAG_ENTRIES)
            .map(e -> new PublicBlogStats.TagCount(e.getKey(), e.getValue()))
            .toList();
        return ToolResult.success(JSON.toJSONString(counts));
    }

    private ToolResult stats() {
        BlogHomeStats home = statsService.getHomeStats();
        boolean available = home != null && home.blogAvailable();
        long articleTotal = available && home.blog() != null ? home.blog().articleTotal() : 0L;
        long draftTotal = available && home.blog() != null ? home.blog().draftTotal() : 0L;
        List<PublicBlogStats.CategoryCount> categoryCounts = publicCategoryCounts(home);
        List<String> recentSlugs = publicOnly(articleService.selectBlogArticleList(new BlogArticle())).stream()
            .sorted(Comparator.comparing(BlogArticle::getPublishDate,
                Comparator.nullsFirst(Comparator.reverseOrder())))
            .limit(MAX_PAGE_SIZE)
            .map(BlogArticle::getSlug)
            .filter(s -> s != null && !s.isBlank())
            .toList();
        long draftCount = draftService.selectBlogDraftList(new BlogDraft()).size();
        PublicBlogStats publicStats = new PublicBlogStats(articleTotal, draftCount,
            categoryCounts, List.of(), recentSlugs, available);
        return ToolResult.success(JSON.toJSONString(publicStats));
    }

    private ToolResult draftList(JSONObject args) {
        int pageSize = clampPageSize(args.getIntValue("pageSize", MAX_PAGE_SIZE));
        List<PublicDraftMeta> drafts = draftService.selectBlogDraftList(new BlogDraft()).stream()
            .limit(pageSize)
            .map(this::toPublicDraft)
            .toList();
        return ToolResult.success(JSON.toJSONString(drafts));
    }

    private ToolResult draftMeta(JSONObject args) {
        Long draftId = args.getLong("draftId");
        if (draftId == null) {
            return ToolResult.failure("INVALID_ARGUMENT", "draftId 不能为空");
        }
        BlogDraft draft = draftService.selectBlogDraftById(draftId);
        if (draft == null) {
            return ToolResult.failure("NOT_FOUND", "未找到草稿: " + draftId);
        }
        return ToolResult.success(JSON.toJSONString(toPublicDraft(draft)));
    }

    // ---- 映射与辅助 ----

    private PublicArticleMeta toPublicMeta(BlogArticle a) {
        return new PublicArticleMeta(
            a.getSlug(),
            a.getTitle(),
            a.getCategories(),
            a.getTags(),
            a.getPublishDate(),
            a.getSummary(),
            a.getWordCount(),
            buildArticleUrl(a.getSlug()),
            toBool(a.getRecommend()),
            toBool(a.getHide()),
            toBool(a.getIsTop()));
    }

    private PublicDraftMeta toPublicDraft(BlogDraft d) {
        return new PublicDraftMeta(
            d.getDraftId(),
            d.getTitle(),
            d.getSlug(),
            d.getCategories(),
            d.getTags(),
            d.getStatus(),
            d.getPublishDate());
    }

    private List<PublicBlogStats.CategoryCount> publicCategoryCounts(BlogHomeStats home) {
        if (home == null || !home.blogAvailable() || home.blog() == null) {
            return List.of();
        }
        List<BlogCategoryCount> source = home.blog().categoryCounts();
        if (source == null) {
            return List.of();
        }
        return source.stream()
            .map(c -> new PublicBlogStats.CategoryCount(c.name(), c.count()))
            .toList();
    }

    /** 只保留已发布文章（hide 不影响文章页可访问，但首页/RSS 口径下隐藏项不计入公开聚合）。 */
    private List<BlogArticle> publicOnly(List<BlogArticle> all) {
        if (all == null) {
            return List.of();
        }
        return all.stream().filter(a -> !"1".equals(a.getHide())).toList();
    }

    private int clampPageSize(Integer requested) {
        if (requested == null || requested <= 0) {
            return MAX_PAGE_SIZE;
        }
        return Math.min(requested, MAX_PAGE_SIZE);
    }

    private static List<String> splitCsv(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String part : value.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    private static Boolean toBool(String flag) {
        return "1".equals(flag);
    }

    private static JSONObject parseArgs(String argumentsJson) {
        if (argumentsJson == null || argumentsJson.isBlank()) {
            return new JSONObject();
        }
        try {
            return JSON.parseObject(argumentsJson);
        } catch (Exception e) {
            // 参数解析失败不抛异常，交给各 Tool 以缺失参数处理；返回空对象让后续校验拒绝
            return new JSONObject();
        }
    }

    /**
     * 由站点配置拼文章公共链接：{@code {siteUrl}/article/{slug}}。
     * 站点未配置或 slug 为空时返回空串，绝不拼出无效或内部地址。
     */
    static String buildArticleUrl(String slug) {
        if (slug == null || slug.isBlank()) {
            return "";
        }
        String siteUrl = ConfigUtils.getString(ConfigConstants.BLOG_SITE_URL, "");
        if (siteUrl == null || siteUrl.isBlank()) {
            return "";
        }
        String base = siteUrl.endsWith("/") ? siteUrl.substring(0, siteUrl.length() - 1) : siteUrl;
        return base + "/article/" + slug;
    }
}
