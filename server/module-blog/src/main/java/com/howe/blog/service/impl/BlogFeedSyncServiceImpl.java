package com.howe.blog.service.impl;

import cn.hutool.core.util.StrUtil;
import com.howe.blog.domain.BlogFeedItem;
import com.howe.blog.domain.BlogLink;
import com.howe.blog.domain.vo.BlogFeedSyncResult;
import com.howe.blog.mapper.BlogLinkMapper;
import com.howe.blog.rss.FeedEntry;
import com.howe.blog.rss.RssFetcher;
import com.howe.blog.rss.RssParser;
import com.howe.blog.service.IBlogFeedItemService;
import com.howe.blog.service.IBlogFeedSyncService;
import com.howe.common.constant.ConfigConstants;
import com.howe.common.exception.ServiceException;
import com.howe.common.utils.ConfigUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * 博客朋友圈同步 服务层实现
 *
 * <p>核心是<b>失败隔离</b>：抓取目标是一堆不受控的第三方站点，
 * 任何一个域名过期、证书失效或返回垃圾 XML 都不能中断整轮同步。</p>
 *
 * @author howe
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BlogFeedSyncServiceImpl implements IBlogFeedSyncService {

    /** 类型：RSS订阅源 */
    private static final String TYPE_FEED = "2";

    /** 状态：启用 */
    private static final String STATUS_ENABLED = "0";

    /** last_error 列宽 500，超长截断后再入库 */
    private static final int MAX_ERROR_LENGTH = 500;

    private final BlogLinkMapper blogLinkMapper;

    private final IBlogFeedItemService blogFeedItemService;

    private final RssFetcher rssFetcher;

    private final RssParser rssParser;

    @Override
    public BlogFeedSyncResult syncAll() {
        return sync(blogLinkMapper.selectEnabledFeeds());
    }

    @Override
    public BlogFeedSyncResult syncOne(Long linkId) {
        BlogLink feed = blogLinkMapper.selectBlogLinkById(linkId, TYPE_FEED);
        if (feed == null) {
            throw new ServiceException("订阅源不存在或类型不匹配");
        }
        if (StrUtil.isBlank(feed.getRssUrl())) {
            throw new ServiceException("该订阅源没有填写订阅地址");
        }
        // syncAll 走 selectEnabledFeeds 天然只取启用中的源，单源同步必须自己补这道校验：
        // 前端虽然禁用了停用源的「同步」按钮，但直接打接口就能绕过，停用源照样抓取入库
        if (!STATUS_ENABLED.equals(feed.getStatus())) {
            throw new ServiceException("订阅源已停用，请先启用再同步");
        }
        return sync(Collections.singletonList(feed));
    }

    /**
     * 逐源同步，单源失败不影响其余源
     *
     * @param feeds 订阅源集合
     * @return 同步结果
     */
    private BlogFeedSyncResult sync(List<BlogLink> feeds) {
        int success = 0;
        int failed = 0;
        int newItems = 0;
        for (BlogLink feed : feeds) {
            try {
                newItems += syncSingle(feed);
                success++;
            } catch (Exception e) {
                // 单源失败必须隔离：一个域名过期不该让整轮同步归零
                failed++;
                String msg = StrUtil.maxLength(StrUtil.blankToDefault(e.getMessage(), e.getClass().getSimpleName()),
                        MAX_ERROR_LENGTH);
                blogLinkMapper.updateSyncResult(feed.getLinkId(), new Date(), msg);
                log.warn("订阅源[{}]同步失败: {}", feed.getLinkName(), msg, e);
            }
        }
        return new BlogFeedSyncResult(feeds.size(), success, failed, newItems);
    }

    /**
     * 同步单个订阅源
     *
     * @param feed 订阅源
     * @return 本次新增条数
     * @throws Exception 抓取或解析失败，由调用方隔离
     */
    private int syncSingle(BlogLink feed) throws Exception {
        int summaryLength = ConfigUtils.getInt(ConfigConstants.BLOG_FEED_SUMMARY_LENGTH, 200);
        byte[] bytes = rssFetcher.fetch(feed.getRssUrl());
        List<FeedEntry> entries = rssParser.parse(bytes, feed.getLinkName(), summaryLength);

        List<BlogFeedItem> candidates = new ArrayList<>(entries.size());
        for (FeedEntry entry : entries) {
            candidates.add(toItem(feed.getLinkId(), entry));
        }
        int added = blogFeedItemService.saveNewItems(feed.getLinkId(), candidates);
        // 成功后把 last_error 清空，否则界面上会一直挂着上一轮的旧错误
        blogLinkMapper.updateSyncResult(feed.getLinkId(), new Date(), "");
        return added;
    }

    /**
     * 解析结果转入库对象
     *
     * @param linkId 订阅源ID
     * @param entry  解析条目
     * @return 入库对象
     */
    private BlogFeedItem toItem(Long linkId, FeedEntry entry) {
        BlogFeedItem item = new BlogFeedItem();
        item.setLinkId(linkId);
        item.setTitle(entry.title());
        item.setAuthor(entry.author());
        item.setUrl(entry.url());
        item.setSummary(entry.summary());
        item.setPubDate(entry.pubDate());
        return item;
    }
}
