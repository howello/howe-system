package com.howe.blog.service;

import com.howe.blog.domain.BlogDraft;

import java.util.List;

/**
 * 博客草稿 服务层
 *
 * @author howe
 */
public interface IBlogDraftService
{
    /**
     * 查询草稿列表
     *
     * @param blogDraft 查询条件
     * @return 草稿集合
     */
    public List<BlogDraft> selectBlogDraftList(BlogDraft blogDraft);

    /**
     * 查询草稿详情（含正文）
     *
     * @param draftId 草稿ID
     * @return 草稿
     */
    public BlogDraft selectBlogDraftById(Long draftId);

    /**
     * 工具专用的草稿详情读取，正文按码点上限截断。
     *
     * <p>SQL 只取 {@code maxContentChars + 1} 个字符判断截断，不整段读取超长正文。
     * 本方法只读，不修改草稿状态。</p>
     *
     * @param draftId 草稿ID
     * @param maxContentChars 正文最大 Unicode 码点数
     * @return 草稿；正文被截断时 {@code remark} 不参与判断，截断标记由调用方比对长度得出
     */
    public BlogDraft readDraftForTool(Long draftId, int maxContentChars);

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
     * 批量删除草稿
     *
     * @param draftIds 草稿ID数组
     * @return 结果
     */
    public int deleteBlogDraftByIds(Long[] draftIds);

    /**
     * 发布草稿：提交到 GitHub 变成正式文章
     *
     * @param draftId 草稿ID
     * @param filePath 目标文件路径，可留空
     * @return 结果
     */
    public int publishDraft(Long draftId, String filePath);
}
