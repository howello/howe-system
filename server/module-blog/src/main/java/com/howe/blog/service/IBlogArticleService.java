package com.howe.blog.service;

import com.howe.blog.domain.BlogArticle;
import com.howe.blog.domain.dto.BlogArticlePublishBody;
import com.howe.blog.domain.vo.BlogPublishResult;
import com.howe.blog.domain.vo.BlogSyncResult;

import java.util.List;

/**
 * 博客文章 服务层
 *
 * @author howe
 */
public interface IBlogArticleService
{
    /**
     * 查询文章列表（走本地索引，支持分页与条件检索）
     *
     * @param blogArticle 查询条件
     * @return 文章集合
     */
    public List<BlogArticle> selectBlogArticleList(BlogArticle blogArticle);

    /**
     * 查询文章详情
     *
     * <p>
     * 正文实时从 GitHub 拉取，同时把最新的 sha 回写索引，避免后续保存时 sha 过期。
     * </p>
     *
     * @param articleId 文章ID
     * @return 含正文的文章
     */
    public BlogArticle selectBlogArticleById(Long articleId);

    /**
     * 工具专用的文章详情读取。
     *
     * <p>与 {@link #selectBlogArticleById(Long)} 的差异：本方法<b>不回写</b>索引中的
     * Git SHA 与同步时间，且正文读取受服务端字节预算约束。{@code includeContent=false}
     * 时只读本地索引，不检查 GitHub 配置、不发起任何网络请求。</p>
     *
     * @param articleId 文章ID
     * @param includeContent 是否读取 GitHub 正文
     * @param maxContentChars 返回正文的最大 Unicode 码点数
     * @return 含正文与截断标记的文章；正文被截断时 {@code contentTruncated} 为 true
     */
    public BlogArticle readArticleForTool(Long articleId, boolean includeContent, int maxContentChars);

    /**
     * 新增文章：生成 markdown 提交到 GitHub，成功后建立本地索引
     *
     * @param blogArticle 文章
     * @return 结果
     */
    public int insertBlogArticle(BlogArticle blogArticle);

    /**
     * 修改文章：更新 GitHub 上的 markdown，成功后同步索引
     *
     * @param blogArticle 文章
     * @return 结果
     */
    public int updateBlogArticle(BlogArticle blogArticle);

    /**
     * 批量删除文章：先删 GitHub 上的文件，再删本地索引
     *
     * @param articleIds 文章ID数组
     * @return 结果
     */
    public int deleteBlogArticleByIds(Long[] articleIds);

    /**
     * 从 GitHub 全量重建索引
     *
     * @return 同步统计
     */
    public BlogSyncResult syncFromGithub();

    /**
     * 处理 GitHub push 事件，按变更文件增量更新索引
     *
     * @param payload webhook 请求体
     * @return 同步统计
     */
    public BlogSyncResult handlePushEvent(String payload);

    /**
     * 发布整篇文章（开放接口用）
     *
     * <p>
     * 按 slug 决定新建还是覆盖，走的仍是「先写 GitHub、成功后更新索引」这条链路。
     * 与后台管理页的差别只在入参形态和没有登录上下文。
     * </p>
     *
     * @param body 发布请求
     * @return 发布结果
     */
    public BlogPublishResult publishArticle(BlogArticlePublishBody body);

    /**
     * 校验文章标识是否唯一
     *
     * @param blogArticle 文章
     * @return 唯一返回 true
     */
    public boolean checkSlugUnique(BlogArticle blogArticle);
}
