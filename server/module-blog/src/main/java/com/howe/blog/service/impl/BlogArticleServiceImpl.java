package com.howe.blog.service.impl;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.howe.blog.domain.BlogArticle;
import com.howe.blog.domain.dto.BlogArticlePublishBody;
import com.howe.blog.domain.vo.BlogPublishResult;
import com.howe.blog.domain.vo.BlogSyncResult;
import com.howe.blog.github.GithubCommit;
import com.howe.blog.github.GithubContentClient;
import com.howe.blog.github.GithubFile;
import com.howe.blog.github.GithubTreeItem;
import com.howe.blog.mapper.BlogArticleMapper;
import com.howe.blog.markdown.Frontmatter;
import com.howe.blog.markdown.FrontmatterUtils;
import com.howe.blog.markdown.MarkdownDocument;
import com.howe.blog.service.IBlogArticleService;
import com.howe.common.exception.ServiceException;
import com.howe.common.utils.SecurityUtils;
import com.howe.common.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 博客文章 服务层实现
 *
 * <p>
 * 文章的真源在 GitHub：所有写操作都是「先改仓库、成功后再更新本地索引」，
 * 这样即使索引更新失败，重新同步一次就能自愈；反过来则会产生仓库里没有的幽灵文章。
 * </p>
 *
 * @author howe
 */
@Service
public class BlogArticleServiceImpl implements IBlogArticleService
{
    private static final Logger log = LoggerFactory.getLogger(BlogArticleServiceImpl.class);

    /** 无登录上下文时，webhook 同步记录的操作者 */
    private static final String GITHUB_OPERATOR = "github";

    /** 无登录上下文时，开放发布接口记录的操作者 */
    private static final String OPEN_API_OPERATOR = "openapi";

    /** 摘要截取长度 */
    private static final int SUMMARY_LENGTH = 200;

    @Autowired
    private BlogArticleMapper blogArticleMapper;

    @Autowired
    private GithubContentClient githubClient;

    @Override
    public List<BlogArticle> selectBlogArticleList(BlogArticle blogArticle)
    {
        return blogArticleMapper.selectBlogArticleList(blogArticle);
    }

    @Override
    public BlogArticle selectBlogArticleById(Long articleId)
    {
        BlogArticle article = blogArticleMapper.selectBlogArticleById(articleId);
        if (article == null)
        {
            throw new ServiceException("文章不存在或已被删除");
        }
        GithubFile file = githubClient.getFile(article.getFilePath());
        if (file == null)
        {
            throw new ServiceException("文章文件在仓库中已不存在：" + article.getFilePath() + "，请重新同步索引");
        }
        MarkdownDocument document = FrontmatterUtils.parse(file.getContent());
        article.setContent(document.getBody());
        // 顺手把索引对齐到仓库最新状态，避免保存时拿着过期的 sha 撞 409
        applyFrontmatter(article, document.getFrontmatter());
        article.setGitSha(file.getSha());
        article.setLastSyncTime(new Date());
        BlogArticle patch = new BlogArticle();
        patch.setArticleId(article.getArticleId());
        patch.setGitSha(file.getSha());
        patch.setLastSyncTime(article.getLastSyncTime());
        blogArticleMapper.updateBlogArticle(patch);
        return article;
    }

    @Override
    public int insertBlogArticle(BlogArticle blogArticle)
    {
        githubClient.assertConfigured();
        if (!checkSlugUnique(blogArticle))
        {
            throw new ServiceException("文章标识已存在：" + blogArticle.getSlug());
        }
        String filePath = resolveFilePath(blogArticle);
        if (blogArticleMapper.selectBlogArticleByFilePath(filePath) != null)
        {
            throw new ServiceException("该路径已存在文章：" + filePath);
        }
        if (githubClient.getFile(filePath) != null)
        {
            throw new ServiceException("仓库中已存在同名文件：" + filePath + "，请换一个文件名或先同步索引");
        }

        blogArticle.setFilePath(filePath);
        fillDefaults(blogArticle);
        String markdown = FrontmatterUtils.build(toFrontmatter(blogArticle), blogArticle.getContent());
        GithubCommit commit = githubClient.putFile(filePath, markdown,
                "docs(blog): 新增文章 " + blogArticle.getTitle(), null);

        blogArticle.setGitSha(commit.getContentSha());
        blogArticle.setLastSyncTime(new Date());
        blogArticle.setCreateBy(currentUsername(OPEN_API_OPERATOR));
        fillDerived(blogArticle);
        return blogArticleMapper.insertBlogArticle(blogArticle);
    }

    @Override
    public int updateBlogArticle(BlogArticle blogArticle)
    {
        githubClient.assertConfigured();
        BlogArticle existing = blogArticleMapper.selectBlogArticleById(blogArticle.getArticleId());
        if (existing == null)
        {
            throw new ServiceException("文章不存在或已被删除");
        }
        if (!checkSlugUnique(blogArticle))
        {
            throw new ServiceException("文章标识已存在：" + blogArticle.getSlug());
        }

        String newPath = resolveFilePath(blogArticle);
        String oldPath = existing.getFilePath();
        boolean pathChanged = !newPath.equals(oldPath);
        if (pathChanged)
        {
            BlogArticle occupied = blogArticleMapper.selectBlogArticleByFilePath(newPath);
            if (occupied != null && !occupied.getArticleId().equals(blogArticle.getArticleId()))
            {
                throw new ServiceException("该路径已存在文章：" + newPath);
            }
        }

        fillDefaults(blogArticle);
        String markdown = FrontmatterUtils.build(toFrontmatter(blogArticle), blogArticle.getContent());
        GithubCommit commit;
        if (pathChanged)
        {
            // Contents API 没有重命名操作，只能新建再删旧。先建后删，中途失败也不会丢内容
            commit = githubClient.putFile(newPath, markdown, "docs(blog): 移动文章 " + blogArticle.getTitle(), null);
            try
            {
                githubClient.deleteFile(oldPath, "docs(blog): 移除旧路径 " + oldPath, existing.getGitSha());
            }
            catch (Exception e)
            {
                log.error("新路径已写入但旧文件删除失败，仓库中会同时存在 {} 和 {}", oldPath, newPath, e);
                throw new ServiceException("文章已写入新路径，但旧文件删除失败：" + e.getMessage() + "，请到仓库手动删除 " + oldPath);
            }
        }
        else
        {
            commit = githubClient.putFile(newPath, markdown, "docs(blog): 更新文章 " + blogArticle.getTitle(),
                    existing.getGitSha());
        }

        blogArticle.setFilePath(newPath);
        blogArticle.setGitSha(commit.getContentSha());
        blogArticle.setLastSyncTime(new Date());
        blogArticle.setUpdateBy(currentUsername(OPEN_API_OPERATOR));
        fillDerived(blogArticle);
        return blogArticleMapper.updateBlogArticle(blogArticle);
    }

    @Override
    public int deleteBlogArticleByIds(Long[] articleIds)
    {
        githubClient.assertConfigured();
        int count = 0;
        for (Long articleId : articleIds)
        {
            BlogArticle article = blogArticleMapper.selectBlogArticleById(articleId);
            if (article == null)
            {
                continue;
            }
            String sha = article.getGitSha();
            if (StringUtils.isEmpty(sha))
            {
                // 索引里没有 sha（比如同步时没落上），临时取一次
                GithubFile file = githubClient.getFile(article.getFilePath());
                sha = file == null ? null : file.getSha();
            }
            if (StringUtils.isNotEmpty(sha))
            {
                githubClient.deleteFile(article.getFilePath(), "docs(blog): 删除文章 " + article.getTitle(), sha);
            }
            else
            {
                log.warn("仓库中找不到文件 {}，仅删除本地索引", article.getFilePath());
            }
            count += blogArticleMapper.deleteBlogArticleById(articleId);
        }
        return count;
    }

    @Override
    public BlogSyncResult syncFromGithub()
    {
        githubClient.assertConfigured();
        BlogSyncResult result = new BlogSyncResult();
        List<GithubTreeItem> items = githubClient.listMarkdownFiles();
        result.setScanned(items.size());

        Set<String> remotePaths = new LinkedHashSet<>();
        for (GithubTreeItem item : items)
        {
            remotePaths.add(item.getPath());
            try
            {
                BlogArticle existing = blogArticleMapper.selectBlogArticleByFilePath(item.getPath());
                if (existing != null && item.getSha().equals(existing.getGitSha()))
                {
                    // sha 没变就不必再读一次文件内容，省 API 调用
                    result.increaseSkipped();
                    continue;
                }
                GithubFile file = githubClient.getFile(item.getPath());
                if (file == null)
                {
                    result.addFailure(item.getPath(), "读取文件失败");
                    continue;
                }
                if (upsertFromGithub(file, existing))
                {
                    result.increaseAdded();
                }
                else
                {
                    result.increaseUpdated();
                }
            }
            catch (Exception e)
            {
                log.error("同步文章失败：{}", item.getPath(), e);
                result.addFailure(item.getPath(), e.getMessage());
            }
        }

        // 仓库里已经没有的，索引也要清掉
        for (String localPath : new ArrayList<>(blogArticleMapper.selectAllFilePaths()))
        {
            if (!remotePaths.contains(localPath))
            {
                blogArticleMapper.deleteBlogArticleByFilePath(localPath);
                result.increaseRemoved();
            }
        }
        return result;
    }

    @Override
    public BlogSyncResult handlePushEvent(String payload)
    {
        BlogSyncResult result = new BlogSyncResult();
        JSONObject event = JSONObject.parseObject(payload);
        String ref = event.getString("ref");
        String branch = githubClient.getBranch();
        if (StringUtils.isNotEmpty(ref) && !ref.equals("refs/heads/" + branch))
        {
            log.info("忽略非目标分支的 push 事件：{}", ref);
            return result;
        }

        Set<String> changed = new LinkedHashSet<>();
        Set<String> removed = new LinkedHashSet<>();
        JSONArray commits = event.getJSONArray("commits");
        String dirPrefix = githubClient.getContentDir() + "/";
        if (commits != null)
        {
            for (int i = 0; i < commits.size(); i++)
            {
                JSONObject commit = commits.getJSONObject(i);
                collectPaths(commit.getJSONArray("added"), dirPrefix, changed, removed, false);
                collectPaths(commit.getJSONArray("modified"), dirPrefix, changed, removed, false);
                collectPaths(commit.getJSONArray("removed"), dirPrefix, changed, removed, true);
            }
        }
        // 同一批提交里先删后加的文件，以最终存在为准
        removed.removeAll(changed);
        result.setScanned(changed.size() + removed.size());

        for (String path : changed)
        {
            try
            {
                GithubFile file = githubClient.getFile(path);
                if (file == null)
                {
                    result.addFailure(path, "读取文件失败");
                    continue;
                }
                BlogArticle existing = blogArticleMapper.selectBlogArticleByFilePath(path);
                if (upsertFromGithub(file, existing))
                {
                    result.increaseAdded();
                }
                else
                {
                    result.increaseUpdated();
                }
            }
            catch (Exception e)
            {
                log.error("webhook 同步文章失败：{}", path, e);
                result.addFailure(path, e.getMessage());
            }
        }
        for (String path : removed)
        {
            if (blogArticleMapper.deleteBlogArticleByFilePath(path) > 0)
            {
                result.increaseRemoved();
            }
        }
        return result;
    }

    @Override
    public BlogPublishResult publishArticle(BlogArticlePublishBody body)
    {
        githubClient.assertConfigured();
        BlogArticle article = body.toArticle();
        BlogArticle existing = blogArticleMapper.selectBlogArticleBySlug(article.getSlug());
        if (existing == null)
        {
            insertBlogArticle(article);
            log.info("开放接口新增文章：{} -> {}", article.getSlug(), article.getFilePath());
            return new BlogPublishResult(article.getArticleId(), article.getSlug(), article.getFilePath(),
                    "/article/" + article.getSlug(), true);
        }

        if (!body.isOverwrite())
        {
            throw new ServiceException("文章标识已存在：" + article.getSlug() + "，如需覆盖请把 overwrite 设为 true");
        }
        article.setArticleId(existing.getArticleId());
        if (StringUtils.isEmpty(article.getFilePath()))
        {
            // 没指定路径就沿用原路径，否则会被当成「移动文章」而白白删建一次
            article.setFilePath(existing.getFilePath());
        }
        if (article.getPublishDate() == null)
        {
            // 覆盖时保留原发布时间，不然每次重发都把日期顶到当下
            article.setPublishDate(existing.getPublishDate());
        }
        updateBlogArticle(article);
        log.info("开放接口覆盖文章：{} -> {}", article.getSlug(), article.getFilePath());
        return new BlogPublishResult(article.getArticleId(), article.getSlug(), article.getFilePath(),
                "/article/" + article.getSlug(), false);
    }

    @Override
    public boolean checkSlugUnique(BlogArticle blogArticle)
    {
        BlogArticle existing = blogArticleMapper.selectBlogArticleBySlug(blogArticle.getSlug());
        if (existing == null)
        {
            return true;
        }
        return blogArticle.getArticleId() != null && existing.getArticleId().equals(blogArticle.getArticleId());
    }

    /**
     * 把仓库里的文件写入索引
     *
     * @return true 表示新增，false 表示更新
     */
    private boolean upsertFromGithub(GithubFile file, BlogArticle existing)
    {
        MarkdownDocument document = FrontmatterUtils.parse(file.getContent());
        Frontmatter frontmatter = document.getFrontmatter();
        if (StringUtils.isEmpty(frontmatter.getId()))
        {
            throw new ServiceException("frontmatter 缺少 id 字段");
        }

        BlogArticle article = new BlogArticle();
        article.setFilePath(file.getPath());
        article.setGitSha(file.getSha());
        article.setLastSyncTime(new Date());
        article.setContent(document.getBody());
        applyFrontmatter(article, frontmatter);
        fillDerived(article);

        if (existing == null)
        {
            // 同一个 slug 可能被挪到了别的路径，这种情况按路径变更处理而不是插入重复 slug
            BlogArticle bySlug = blogArticleMapper.selectBlogArticleBySlug(article.getSlug());
            if (bySlug != null)
            {
                article.setArticleId(bySlug.getArticleId());
                blogArticleMapper.updateBlogArticle(article);
                return false;
            }
            article.setCreateBy(currentUsername(GITHUB_OPERATOR));
            blogArticleMapper.insertBlogArticle(article);
            return true;
        }
        article.setArticleId(existing.getArticleId());
        article.setUpdateBy(currentUsername(GITHUB_OPERATOR));
        blogArticleMapper.updateBlogArticle(article);
        return false;
    }

    /**
     * frontmatter 写进实体
     */
    private void applyFrontmatter(BlogArticle article, Frontmatter frontmatter)
    {
        article.setSlug(frontmatter.getId());
        article.setTitle(frontmatter.getTitle());
        article.setCategories(frontmatter.getCategories());
        article.setTags(String.join(",", frontmatter.getTags()));
        article.setPublishDate(frontmatter.getDate());
        article.setCover(frontmatter.getCover());
        article.setRecommend(toFlag(frontmatter.getRecommend()));
        article.setHide(toFlag(frontmatter.getHide()));
        article.setIsTop(toFlag(frontmatter.getTop()));
    }

    /**
     * 实体转 frontmatter
     */
    private Frontmatter toFrontmatter(BlogArticle article)
    {
        Frontmatter frontmatter = new Frontmatter();
        frontmatter.setTitle(article.getTitle());
        frontmatter.setCategories(article.getCategories());
        frontmatter.setTags(splitToList(article.getTags()));
        frontmatter.setId(article.getSlug());
        frontmatter.setDate(article.getPublishDate());
        frontmatter.setCover(StringUtils.isEmpty(article.getCover()) ? null : article.getCover());
        // 三个开关只在为「是」时写进 frontmatter：仓库现有文章都没有这些字段，
        // 写 false 会凭空产生 diff，而语义上与不写等价
        frontmatter.setRecommend(isYes(article.getRecommend()) ? Boolean.TRUE : null);
        frontmatter.setHide(isYes(article.getHide()) ? Boolean.TRUE : null);
        frontmatter.setTop(isYes(article.getIsTop()) ? Boolean.TRUE : null);
        return frontmatter;
    }

    /**
     * 计算摘要与字数
     */
    private void fillDerived(BlogArticle article)
    {
        String body = StringUtils.defaultString(article.getContent());
        article.setWordCount(body.replaceAll("\\s+", "").length());
        article.setSummary(buildSummary(body));
    }

    /**
     * 补默认值
     */
    private void fillDefaults(BlogArticle article)
    {
        if (article.getPublishDate() == null)
        {
            article.setPublishDate(new Date());
        }
        if (StringUtils.isEmpty(article.getCategories()))
        {
            throw new ServiceException("分类不能为空，blog-ui 的 schema 要求 categories 必填");
        }
    }

    /**
     * 生成摘要：去掉 markdown 标记后截断
     */
    private String buildSummary(String body)
    {
        String text = body.replaceAll("(?s)```.*?```", " ")
                .replaceAll("!\\[[^\\]]*\\]\\([^)]*\\)", " ")
                .replaceAll("\\[([^\\]]*)\\]\\([^)]*\\)", "$1")
                .replaceAll("(?m)^\\s{0,3}#{1,6}\\s*", "")
                .replaceAll("[*_>`~:]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return StrUtil.maxLength(text, SUMMARY_LENGTH);
    }

    /**
     * 决定文章在仓库里的完整路径
     *
     * <p>
     * 前端可以填相对文章目录的路径（如 {@code interview-notes/07-netty.md}），
     * 留空时用 {@code <slug>.md}。已经带上目录前缀的原样使用。
     * </p>
     */
    private String resolveFilePath(BlogArticle article)
    {
        String contentDir = githubClient.getContentDir();
        String path = StringUtils.defaultString(article.getFilePath()).trim().replace('\\', '/');
        path = StringUtils.strip(path, "/");
        if (StringUtils.isEmpty(path))
        {
            path = article.getSlug() + ".md";
        }
        else if (!GithubContentClient.isMarkdown(path))
        {
            path = path + ".md";
        }
        if (path.contains(".."))
        {
            throw new ServiceException("文件路径不能包含 ..");
        }
        return path.startsWith(contentDir + "/") ? path : contentDir + "/" + path;
    }

    /**
     * 收集 push 事件里位于文章目录下的 markdown 路径
     */
    private void collectPaths(JSONArray paths, String dirPrefix, Set<String> changed, Set<String> removed,
            boolean isRemoved)
    {
        if (paths == null)
        {
            return;
        }
        for (int i = 0; i < paths.size(); i++)
        {
            String path = paths.getString(i);
            if (path == null || !path.startsWith(dirPrefix) || !GithubContentClient.isMarkdown(path))
            {
                continue;
            }
            if (isRemoved)
            {
                removed.add(path);
            }
            else
            {
                changed.add(path);
            }
        }
    }

    private List<String> splitToList(String value)
    {
        List<String> list = new ArrayList<>();
        if (StringUtils.isEmpty(value))
        {
            return list;
        }
        Set<String> seen = new HashSet<>();
        for (String item : value.split(","))
        {
            String trimmed = item.trim();
            if (StringUtils.isNotEmpty(trimmed) && seen.add(trimmed))
            {
                list.add(trimmed);
            }
        }
        return list;
    }

    private static String toFlag(Boolean value)
    {
        return Boolean.TRUE.equals(value) ? "1" : "0";
    }

    private static boolean isYes(String flag)
    {
        return "1".equals(flag);
    }

    /**
     * 取当前登录用户名
     *
     * <p>
     * webhook 与开放发布接口都是匿名调用的，此时没有登录上下文，用调用方给的标记兜底，
     * 免得 {@code SecurityUtils.getUsername()} 直接抛异常把写入流程打断。
     * </p>
     *
     * @param fallback 无登录上下文时使用的操作者标记
     */
    private static String currentUsername(String fallback)
    {
        try
        {
            return SecurityUtils.getUsername();
        }
        catch (Exception e)
        {
            return fallback;
        }
    }
}
