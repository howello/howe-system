package com.howe.blog.service;

import com.howe.blog.domain.BlogFeedItem;

import java.util.List;

/**
 * 博客朋友圈条目 服务层
 *
 * @author howe
 */
public interface IBlogFeedItemService {
    /**
     * 查询条目列表
     *
     * @param blogFeedItem 查询条件
     * @return 条目集合
     */
    public List<BlogFeedItem> selectBlogFeedItemList(BlogFeedItem blogFeedItem);

    /**
     * 查询对外可见的条目列表，只含启用中订阅源的条目
     *
     * @return 条目集合
     */
    public List<BlogFeedItem> selectPublicFeedItemList();

    /**
     * 按主键批量删除条目
     *
     * @param itemIds 主键ID数组
     * @return 结果
     */
    public int deleteBlogFeedItemByIds(Long[] itemIds);

    /**
     * 删除某个订阅源的全部条目
     *
     * @param linkId 订阅源ID
     * @return 结果
     */
    public int deleteByLinkId(Long linkId);

    /**
     * 去重入库：只插入 url 尚未存在的条目
     *
     * @param linkId     订阅源ID
     * @param candidates 本次抓取到的候选条目
     * @return 实际新增条数
     */
    public int saveNewItems(Long linkId, List<BlogFeedItem> candidates);
}
