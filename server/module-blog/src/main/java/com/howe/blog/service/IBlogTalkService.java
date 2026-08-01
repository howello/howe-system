package com.howe.blog.service;

import com.howe.blog.domain.BlogTalk;

import java.util.List;

/**
 * 博客说说 服务层
 *
 * <p>正文以 markdown 原文存取，本层不做任何 HTML 转换或转义——
 * 渲染是 blog-ui 的职责，后端一旦转换就无法还原原始语法。</p>
 *
 * @author howe
 */
public interface IBlogTalkService {
    /**
     * 查询说说列表
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
    public BlogTalk selectBlogTalkById(Long talkId);

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
     * @return 结果
     */
    public int updateBlogTalk(BlogTalk blogTalk);

    /**
     * 批量删除说说
     *
     * @param talkIds 主键ID数组
     * @return 结果
     */
    public int deleteBlogTalkByIds(Long[] talkIds);
}
