package com.howe.blog.service;

import com.howe.blog.domain.BlogArticle;
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
     * 校验文章标识是否唯一
     *
     * @param blogArticle 文章
     * @return 唯一返回 true
     */
    public boolean checkSlugUnique(BlogArticle blogArticle);
}
