package com.howe.blog.service;

import com.howe.blog.domain.BlogLink;

import java.util.List;

/**
 * 博客站点（友链/RSS订阅源） 服务层
 *
 * <p>友链与订阅源共用 blog_link 表，因此所有<b>单条操作方法都带 linkType 入参</b>，
 * 并由实现类检查影响行数——跨类型操作会被拦下并抛 ServiceException。</p>
 *
 * @author howe
 */
public interface IBlogLinkService {
    /**
     * 查询站点列表
     *
     * @param blogLink 查询条件（linkType 由 Controller 强制固定）
     * @return 站点集合
     */
    public List<BlogLink> selectBlogLinkList(BlogLink blogLink);

    /**
     * 按主键与类型查询站点
     *
     * @param linkId   主键ID
     * @param linkType 类型（1友链 2RSS订阅源）
     * @return 站点
     */
    public BlogLink selectBlogLinkById(Long linkId, String linkType);

    /**
     * 新增站点
     *
     * @param blogLink 站点
     * @return 结果
     */
    public int insertBlogLink(BlogLink blogLink);

    /**
     * 修改站点
     *
     * @param blogLink 站点（linkType 由 Controller 强制固定）
     * @return 结果
     */
    public int updateBlogLink(BlogLink blogLink);

    /**
     * 批量删除站点
     *
     * @param linkIds  主键ID数组
     * @param linkType 类型（1友链 2RSS订阅源）
     * @return 结果
     */
    public int deleteBlogLinkByIds(Long[] linkIds, String linkType);
}
