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
     * 工具专用的说说列表查询。
     *
     * <p>正文在 SQL 侧截取到 {@code maxContentChars + 1} 个字符，避免超长说说的正文整段读入内存；
     * 筛选与排序保持管理端列表语义。</p>
     *
     * @param blogTalk 查询条件
     * @param maxContentChars 每条正文最大 Unicode 码点数
     * @return 说说集合
     */
    public List<BlogTalk> selectBlogTalkListForTool(BlogTalk blogTalk, int maxContentChars);

    /**
     * 按主键查询说说
     *
     * @param talkId 主键ID
     * @return 说说
     */
    public BlogTalk selectBlogTalkById(Long talkId);

    /**
     * 工具专用的说说详情读取，正文按码点上限截断。
     *
     * @param talkId 主键ID
     * @param maxContentChars 正文最大 Unicode 码点数
     * @return 说说；正文被截断时由调用方比对长度得出标记
     */
    public BlogTalk readBlogTalkForTool(Long talkId, int maxContentChars);

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
     * 按数据版本 CAS 更新说说。
     *
     * @param patch 补丁，必须带 talkId
     * @param expectedVersion 期望数据版本
     * @return 更新后的新版本
     * @throws com.howe.common.exception.ServiceException 说说不存在或版本冲突
     */
    public long updateBlogTalkCas(BlogTalk patch, long expectedVersion);

    /**
     * 批量删除说说
     *
     * @param talkIds 主键ID数组
     * @return 结果
     */
    public int deleteBlogTalkByIds(Long[] talkIds);
}
