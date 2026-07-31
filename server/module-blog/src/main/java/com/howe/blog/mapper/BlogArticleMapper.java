package com.howe.blog.mapper;

import com.howe.blog.domain.BlogArticle;

import java.util.List;

/**
 * 博客文章索引 数据层
 *
 * @author howe
 */
public interface BlogArticleMapper
{
    /**
     * 查询文章索引列表
     *
     * @param blogArticle 查询条件
     * @return 文章索引集合
     */
    public List<BlogArticle> selectBlogArticleList(BlogArticle blogArticle);

    /**
     * 按主键查询文章索引
     *
     * @param articleId 文章ID
     * @return 文章索引
     */
    public BlogArticle selectBlogArticleById(Long articleId);

    /**
     * 按文章标识查询
     *
     * @param slug 文章标识
     * @return 文章索引
     */
    public BlogArticle selectBlogArticleBySlug(String slug);

    /**
     * 按仓库文件路径查询
     *
     * @param filePath 仓库内文件路径
     * @return 文章索引
     */
    public BlogArticle selectBlogArticleByFilePath(String filePath);

    /**
     * 查询全部索引的文件路径
     *
     * <p>
     * 同步时用来找出仓库里已经不存在、但本地索引仍留着的记录。
     * </p>
     *
     * @return 文件路径集合
     */
    public List<String> selectAllFilePaths();

    /**
     * 新增文章索引
     *
     * @param blogArticle 文章索引
     * @return 结果
     */
    public int insertBlogArticle(BlogArticle blogArticle);

    /**
     * 修改文章索引
     *
     * @param blogArticle 文章索引
     * @return 结果
     */
    public int updateBlogArticle(BlogArticle blogArticle);

    /**
     * 按主键删除文章索引
     *
     * @param articleId 文章ID
     * @return 结果
     */
    public int deleteBlogArticleById(Long articleId);

    /**
     * 按主键批量删除文章索引
     *
     * @param articleIds 文章ID数组
     * @return 结果
     */
    public int deleteBlogArticleByIds(Long[] articleIds);

    /**
     * 按仓库文件路径删除文章索引
     *
     * @param filePath 仓库内文件路径
     * @return 结果
     */
    public int deleteBlogArticleByFilePath(String filePath);
}
