package com.howe.blog.service.impl;

import com.howe.blog.domain.BlogFeedItem;
import com.howe.blog.mapper.BlogFeedItemMapper;
import com.howe.blog.service.IBlogFeedItemService;
import com.howe.common.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 博客朋友圈条目 服务层实现
 *
 * @author howe
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BlogFeedItemServiceImpl implements IBlogFeedItemService {

    private final BlogFeedItemMapper blogFeedItemMapper;

    @Override
    public List<BlogFeedItem> selectBlogFeedItemList(BlogFeedItem blogFeedItem) {
        return blogFeedItemMapper.selectBlogFeedItemList(blogFeedItem);
    }

    @Override
    public List<BlogFeedItem> selectPublicFeedItemList() {
        return blogFeedItemMapper.selectPublicFeedItemList();
    }

    @Override
    public int deleteBlogFeedItemByIds(Long[] itemIds) {
        // 空数组会让 where item_id in () 变成 SQL 语法错误 500，与另两个 Service 保持一致
        if (itemIds == null || itemIds.length == 0) {
            throw new ServiceException("请选择要删除的数据");
        }
        return blogFeedItemMapper.deleteBlogFeedItemByIds(itemIds);
    }

    @Override
    public int deleteByLinkId(Long linkId) {
        return blogFeedItemMapper.deleteByLinkId(linkId);
    }

    /**
     * 先查后插而不是 insert ignore：
     * insert ignore 会把主键冲突之外的错误一并吞掉，且拿不到准确的新增条数，
     * 而「本次新增 N 条」是同步结果要回报给用户的关键信息。
     * uk_blog_feed_item_url 唯一键留作并发下的最终保障。
     */
    @Override
    public int saveNewItems(Long linkId, List<BlogFeedItem> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return 0;
        }
        List<String> urls = candidates.stream().map(BlogFeedItem::getUrl).toList();
        Set<String> seen = new HashSet<>(blogFeedItemMapper.selectExistingUrls(urls));
        List<BlogFeedItem> fresh = new ArrayList<>();
        for (BlogFeedItem item : candidates) {
            // seen 同时承担两个职责：挡掉库里已有的，也挡掉同一批次内重复出现的 url，
            // 否则批量插入会撞唯一键，让整个源的同步失败
            if (seen.add(item.getUrl())) {
                fresh.add(item);
            }
        }
        if (fresh.isEmpty()) {
            return 0;
        }
        blogFeedItemMapper.batchInsert(fresh);
        return fresh.size();
    }
}
