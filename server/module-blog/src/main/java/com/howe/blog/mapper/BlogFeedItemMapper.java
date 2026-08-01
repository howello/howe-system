package com.howe.blog.mapper;

import com.howe.blog.domain.BlogFeedItem;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 博客朋友圈条目 数据层
 *
 * @author howe
 */
public interface BlogFeedItemMapper {
    /**
     * 查询条目列表
     *
     * @param blogFeedItem 查询条件，linkId 非空时只查该源
     * @return 条目集合
     */
    public List<BlogFeedItem> selectBlogFeedItemList(BlogFeedItem blogFeedItem);

    /**
     * 查询对外可见的条目列表，供 blog-ui 的匿名接口使用
     *
     * <p>与管理端查询的差别：只取<b>启用中的订阅源</b>的条目。
     * 订阅源被停用或被删除后，其条目不得继续出现在站点上。</p>
     *
     * @return 条目集合
     */
    public List<BlogFeedItem> selectPublicFeedItemList();

    /**
     * 在给定 url 集合中筛出已入库的部分，供「先查后插」去重
     *
     * @param urls 候选 url 集合，不得为空
     * @return 已存在的 url 集合
     */
    public List<String> selectExistingUrls(@Param("urls") List<String> urls);

    /**
     * 批量插入条目
     *
     * @param list 条目集合，不得为空
     * @return 结果
     */
    public int batchInsert(@Param("list") List<BlogFeedItem> list);

    /**
     * 按主键批量删除条目
     *
     * @param itemIds 主键ID数组
     * @return 结果
     */
    public int deleteBlogFeedItemByIds(Long[] itemIds);

    /**
     * 删除某个订阅源的全部条目，订阅源被删时一并清理
     *
     * @param linkId 订阅源ID
     * @return 结果
     */
    public int deleteByLinkId(Long linkId);
}
