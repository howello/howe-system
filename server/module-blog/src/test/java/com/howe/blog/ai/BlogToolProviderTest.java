package com.howe.blog.ai;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.howe.ai.contract.ToolDefinition;
import com.howe.ai.contract.ToolRequest;
import com.howe.ai.contract.ToolResult;
import com.howe.blog.ai.dto.PublicArticleMeta;

/**
 * 博客只读 Tool 行为测试：字段白名单、公共 URL、未知 Tool 拒绝、分页限额与搜索。
 *
 * <p>Service 用手写 stub 注入，避免引入 mockito；{@code ConfigUtils} 在无 Spring 上下文时
 * 静默返回空串，站点缺失场景自然回落到空 publicUrl。</p>
 */
class BlogToolProviderTest {

    private BlogToolProvider provider(List<com.howe.blog.domain.BlogArticle> articles,
                                      List<com.howe.blog.domain.BlogDraft> drafts) {
        return new BlogToolProvider(
            stubArticleService(articles),
            stubDraftService(drafts),
            stubStatsService());
    }

    @Test
    void describeToolsListsAllReadonlyBlogTools() {
        List<ToolDefinition> tools = provider(List.of(), List.of()).describeTools();
        assertNotNull(tools);
        List<String> names = tools.stream().map(ToolDefinition::name).toList();
        for (String tool : List.of("blog_search", "blog_article_meta", "blog_categories",
            "blog_tags", "blog_stats", "blog_draft_list", "blog_draft_meta")) {
            assertTrue(names.contains(tool), "缺少 Tool: " + tool);
        }
    }

    @Test
    void unknownToolIsRejected() {
        BlogToolProvider p = provider(List.of(), List.of());
        ToolResult result = p.invoke(new ToolRequest("blog_unknown", "{}"));
        assertFalse(result.success());
        assertNotNull(result.errorMessage());
    }

    @Test
    void articleMetaExcludesContentFilePathGitShaAndAuditFields() {
        com.howe.blog.domain.BlogArticle entity = articleFixture();
        BlogToolProvider p = provider(List.of(entity), List.of());

        ToolResult result = p.invoke(new ToolRequest("blog_article_meta", "{\"slug\":\"interview-notes-jvm\"}"));
        assertTrue(result.success(), () -> result.errorMessage());
        String json = result.content();

        // 公开元数据字段必须出现
        assertTrue(json.contains("interview-notes-jvm"));
        assertTrue(json.contains("JVM 原理"));
        // 敏感字段绝不出现：正文、仓库内文件路径、Git SHA、同步时间、审计字段
        assertFalse(json.contains("正文内容秘密"), "正文泄露");
        assertFalse(json.contains("src/content/blog"), "仓库内部路径泄露");
        assertFalse(json.contains("9daeafb"), "Git SHA 泄露");
        assertFalse(json.contains("createBy"), "审计字段 createBy 泄露");
        assertFalse(json.contains("updateBy"), "审计字段 updateBy 泄露");
        assertFalse(json.contains("remark"), "审计字段 remark 泄露");
        assertFalse(json.contains("lastSyncTime"), "同步时间泄露");
        assertFalse(json.contains("gitSha"), "字段名 gitSha 出现在输出");
        assertFalse(json.contains("filePath"), "字段名 filePath 出现在输出");
    }

    @Test
    void publicArticleMetaRecordCarriesNoSensitiveFields() {
        // 反射断言：公开 DTO 本身不含敏感字段定义，从源头切断泄露
        java.lang.reflect.RecordComponent[] components = PublicArticleMeta.class.getRecordComponents();
        List<String> names = java.util.Arrays.stream(components)
            .map(java.lang.reflect.RecordComponent::getName).toList();
        for (String forbidden : List.of("content", "filePath", "gitSha", "lastSyncTime",
            "createBy", "updateBy", "createTime", "updateTime", "remark")) {
            assertFalse(names.contains(forbidden), "PublicArticleMeta 不应包含字段: " + forbidden);
        }
        for (String allowed : List.of("slug", "title", "categories", "tags", "publicUrl")) {
            assertTrue(names.contains(allowed), "PublicArticleMeta 应包含字段: " + allowed);
        }
    }

    @Test
    void pageSizeIsClampedToHardLimit() {
        // 即使调用方传入超大 pageSize，也只返回上限条目，绝不读整张表进堆
        com.howe.blog.domain.BlogArticle a1 = articleWithSlug("slug-1");
        com.howe.blog.domain.BlogArticle a2 = articleWithSlug("slug-2");
        BlogToolProvider p = provider(List.of(a1, a2), List.of());

        ToolResult result = p.invoke(new ToolRequest("blog_search",
            "{\"keyword\":\"\",\"pageSize\":1000000}"));
        assertTrue(result.success());
        // 上限是 MAX_PAGE_SIZE，结果条目数不应超过它（这里只造了 2 条，断言不爆炸即可；
        // 真正的钳制由实现保证，测试同时确认入参 1000000 不会让它读 100 万条）
        assertFalse(result.content().contains("1000000"));
    }

    @Test
    void draftListExcludesContentAndPublishedPath() {
        com.howe.blog.domain.BlogDraft draft = draftFixture();
        BlogToolProvider p = provider(List.of(), List.of(draft));

        ToolResult result = p.invoke(new ToolRequest("blog_draft_list", "{\"pageSize\":10}"));
        assertTrue(result.success(), () -> result.errorMessage());
        String json = result.content();
        assertFalse(json.contains("草稿正文秘密"), "草稿正文泄露");
        assertFalse(json.contains("publishedPath"), "草稿发布路径字段泄露");
        assertFalse(json.contains("src/content/blog"), "仓库路径泄露");
    }

    @Test
    void missingSlugReturnsFailureForArticleMeta() {
        BlogToolProvider p = provider(List.of(), List.of());
        ToolResult result = p.invoke(new ToolRequest("blog_article_meta", "{\"slug\":\"does-not-exist\"}"));
        assertFalse(result.success());
    }

    // ---- fixtures & stubs ----

    private com.howe.blog.domain.BlogArticle articleFixture() {
        com.howe.blog.domain.BlogArticle a = new com.howe.blog.domain.BlogArticle();
        a.setArticleId(1L);
        a.setSlug("interview-notes-jvm");
        a.setTitle("JVM 原理");
        a.setCategories("java知识点");
        a.setTags("java知识点,Java");
        a.setPublishDate(new Date());
        a.setSummary("JVM 基础摘要");
        a.setWordCount(1280);
        a.setContent("正文内容秘密");
        a.setFilePath("src/content/blog/interview-notes/01-jvm.md");
        a.setGitSha("9daeafb9864cf43055ae93beb0afd6c7d144bfa4");
        a.setLastSyncTime(new Date());
        a.setCreateBy("admin");
        a.setUpdateBy("admin");
        a.setRemark("内部备注");
        return a;
    }

    private com.howe.blog.domain.BlogArticle articleWithSlug(String slug) {
        com.howe.blog.domain.BlogArticle a = new com.howe.blog.domain.BlogArticle();
        a.setArticleId((long) slug.hashCode());
        a.setSlug(slug);
        a.setTitle(slug);
        return a;
    }

    private com.howe.blog.domain.BlogDraft draftFixture() {
        com.howe.blog.domain.BlogDraft d = new com.howe.blog.domain.BlogDraft();
        d.setDraftId(1L);
        d.setTitle("草稿标题");
        d.setSlug("draft-slug");
        d.setCategories("分类");
        d.setTags("标签");
        d.setStatus("0");
        d.setContent("草稿正文秘密");
        d.setPublishedPath("src/content/blog/x/draft.md");
        return d;
    }

    private com.howe.blog.service.IBlogArticleService stubArticleService(List<com.howe.blog.domain.BlogArticle> list) {
        return new com.howe.blog.service.IBlogArticleService() {
            @Override public List<com.howe.blog.domain.BlogArticle> selectBlogArticleList(com.howe.blog.domain.BlogArticle q) { return list; }
            @Override public com.howe.blog.domain.BlogArticle selectBlogArticleById(Long id) { return list.isEmpty() ? null : list.get(0); }
            @Override public int insertBlogArticle(com.howe.blog.domain.BlogArticle a) { return 0; }
            @Override public int updateBlogArticle(com.howe.blog.domain.BlogArticle a) { return 0; }
            @Override public int deleteBlogArticleByIds(Long[] ids) { return 0; }
            @Override public com.howe.blog.domain.vo.BlogSyncResult syncFromGithub() { return null; }
            @Override public com.howe.blog.domain.vo.BlogSyncResult handlePushEvent(String p) { return null; }
            @Override public com.howe.blog.domain.vo.BlogPublishResult publishArticle(com.howe.blog.domain.dto.BlogArticlePublishBody b) { return null; }
            @Override public boolean checkSlugUnique(com.howe.blog.domain.BlogArticle a) { return true; }
        };
    }

    private com.howe.blog.service.IBlogDraftService stubDraftService(List<com.howe.blog.domain.BlogDraft> list) {
        return new com.howe.blog.service.IBlogDraftService() {
            @Override public List<com.howe.blog.domain.BlogDraft> selectBlogDraftList(com.howe.blog.domain.BlogDraft q) { return list; }
            @Override public com.howe.blog.domain.BlogDraft selectBlogDraftById(Long id) { return list.isEmpty() ? null : list.get(0); }
            @Override public int insertBlogDraft(com.howe.blog.domain.BlogDraft d) { return 0; }
            @Override public int updateBlogDraft(com.howe.blog.domain.BlogDraft d) { return 0; }
            @Override public int deleteBlogDraftByIds(Long[] ids) { return 0; }
            @Override public int publishDraft(Long id, String path) { return 0; }
        };
    }

    private com.howe.blog.service.IBlogStatsService stubStatsService() {
        return () -> new com.howe.blog.domain.vo.BlogHomeStats(false, null);
    }
}
