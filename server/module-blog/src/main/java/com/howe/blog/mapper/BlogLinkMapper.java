package com.howe.blog.mapper;

import com.howe.blog.domain.BlogLink;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

/**
 * 博客站点（友链/RSS订阅源） 数据层
 *
 * <p>友链与订阅源共用 blog_link 表，因此<b>所有单条操作方法都必须带 linkType</b>：
 * 只按主键约束的话，持有 blog:feed:remove 的角色传一个友链 ID 就能删掉友链，
 * 双 Controller 的权限隔离会形同虚设。</p>
 *
 * @author howe
 */
public interface BlogLinkMapper {
    /**
     * 查询站点列表
     *
     * @param blogLink 查询条件
     * @return 站点集合
     */
    public List<BlogLink> selectBlogLinkList(BlogLink blogLink);

    /**
     * 按主键与类型查询站点
     *
     * @param linkId   主键ID
     * @param linkType 类型（1友链 2RSS订阅源）
     * @return 站点，类型不匹配时返回 null
     */
    public BlogLink selectBlogLinkById(@Param("linkId") Long linkId, @Param("linkType") String linkType);

    /**
     * 新增站点
     *
     * @param blogLink 站点
     * @return 结果
     */
    public int insertBlogLink(BlogLink blogLink);

    /**
     * 修改站点，where 同时约束 link_id 与 link_type
     *
     * @param blogLink 站点
     * @return 影响行数，0 表示不存在或类型不匹配
     */
    public int updateBlogLink(BlogLink blogLink);

    /**
     * 按主键批量删除站点，where 同时约束 link_id 与 link_type
     *
     * @param linkIds  主键ID数组
     * @param linkType 类型（1友链 2RSS订阅源）
     * @return 影响行数，小于入参长度表示存在不匹配项
     */
    public int deleteBlogLinkByIds(@Param("linkIds") Long[] linkIds, @Param("linkType") String linkType);

    /**
     * 查询全部启用且配置了地址的 RSS 订阅源，供同步任务使用
     *
     * @return 订阅源集合
     */
    public List<BlogLink> selectEnabledFeeds();

    /**
     * 回写同步结果
     *
     * @param linkId       订阅源ID
     * @param lastSyncTime 本次同步时间
     * @param lastError    失败原因，成功时传空串清空
     * @return 结果
     */
    public int updateSyncResult(@Param("linkId") Long linkId, @Param("lastSyncTime") Date lastSyncTime,
                                @Param("lastError") String lastError);
}
