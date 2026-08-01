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
     * 按主键查询说说
     *
     * @param talkId 主键ID
     * @return 说说
     */
    public BlogTalk selectBlogTalkById(@Param("talkId") Long talkId);

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
     * 按主键批量删除说说
     *
     * @param talkIds 主键ID数组
     * @return 结果
     */
    public int deleteBlogTalkByIds(@Param("talkIds") Long[] talkIds);
}
