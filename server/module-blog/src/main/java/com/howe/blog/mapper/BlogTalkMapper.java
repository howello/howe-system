package com.howe.blog.mapper;

import com.howe.blog.domain.BlogTalk;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 博客说说 数据层
 *
 * <p>说说独占 blog_talk 表，不存在 blog_link 那种共表越权问题，
 * 因此单条操作只按主键约束即可。</p>
 *
 * @author howe
 */
public interface BlogTalkMapper {
    /**
     * 查询说说列表，置顶优先、同级按发布时间倒序
     *
     * @param blogTalk 查询条件
     * @return 说说集合
     */
    public List<BlogTalk> selectBlogTalkList(BlogTalk blogTalk);

    /**
     * 工具专用的说说列表查询，正文按 SQL 上限截取。
     *
     * <p>列表页只需要短摘要，不能让超长正文整段读入内存；配合平台分页主查询使用。</p>
     *
     * @param blogTalk 查询条件
     * @param maxChars 每条正文最大字符数
     * @return 说说集合，正文最多 {@code maxChars + 1} 个字符
     */
    public List<BlogTalk> selectBlogTalkListForTool(@Param("query") BlogTalk blogTalk,
            @Param("maxChars") int maxChars);

    /**
     * 按主键查询说说
     *
     * @param talkId 主键ID
     * @return 说说
     */
    public BlogTalk selectBlogTalkById(@Param("talkId") Long talkId);

    /**
     * 按主键查询说说的工具专用有界视图。
     *
     * <p>SQL 层只截取 {@code maxChars + 1} 个字符用于判断是否截断，避免把超长正文整段读入内存。
     * 不修改任何说说的状态或计数。</p>
     *
     * @param talkId 主键ID
     * @param maxChars 正文最大字符数
     * @return 说说；正文字段最多 {@code maxChars + 1} 个字符
     */
    public BlogTalk selectBlogTalkForTool(@Param("talkId") Long talkId, @Param("maxChars") int maxChars);

    /**
     * 新增说说
     *
     * @param blogTalk 说说
     * @return 结果
     */
    public int insertBlogTalk(BlogTalk blogTalk);

    /**
     * 修改说说
     *
     * @param blogTalk 说说
     * @return 影响行数，0 表示数据不存在
     */
    public int updateBlogTalk(BlogTalk blogTalk);

    /**
     * 按数据版本 CAS 更新说说。
     *
     * @param talk 带 talkId 的补丁
     * @param expectedVersion 期望数据版本
     * @return 影响行数；0 表示版本冲突
     */
    public int updateBlogTalkCas(@Param("talk") BlogTalk talk, @Param("expectedVersion") long expectedVersion);

    /**
     * 按主键批量删除说说
     *
     * @param talkIds 主键ID数组
     * @return 结果
     */
    public int deleteBlogTalkByIds(@Param("talkIds") Long[] talkIds);
}
