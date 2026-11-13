package com.howe.blog.mapper;

import com.howe.blog.domain.BlogDraft;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 博客草稿 数据层
 *
 * @author howe
 */
public interface BlogDraftMapper
{
    /**
     * 查询草稿列表
     *
     * @param blogDraft 查询条件
     * @return 草稿集合
     */
    public List<BlogDraft> selectBlogDraftList(BlogDraft blogDraft);

    /**
     * 按主键查询草稿
     *
     * @param draftId 草稿ID
     * @return 草稿
     */
    public BlogDraft selectBlogDraftById(Long draftId);

    /**
     * 按主键查询草稿的工具专用有界视图。
     *
     * <p>SQL 层只截取 {@code maxChars + 1} 个字符用于判断是否截断，避免把无限正文整段读入内存。
     * 不修改任何草稿字段。</p>
     *
     * @param draftId 草稿ID
     * @param maxChars 正文最大字符数
     * @return 草稿；正文字段最多 {@code maxChars + 1} 个字符
     */
    public BlogDraft selectBlogDraftForTool(@Param("draftId") Long draftId, @Param("maxChars") int maxChars);

    /**
     * 新增草稿
     *
     * @param blogDraft 草稿
     * @return 结果
     */
    public int insertBlogDraft(BlogDraft blogDraft);

    /**
     * 修改草稿
     *
     * @param blogDraft 草稿
     * @return 结果
     */
    public int updateBlogDraft(BlogDraft blogDraft);

    /**
     * 按主键删除草稿
     *
     * @param draftId 草稿ID
     * @return 结果
     */
    public int deleteBlogDraftById(Long draftId);

    /**
     * 按主键批量删除草稿
     *
     * @param draftIds 草稿ID数组
     * @return 结果
     */
    public int deleteBlogDraftByIds(Long[] draftIds);
}
