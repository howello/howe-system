package com.howe.blog.service.impl;

import com.howe.blog.domain.BlogLink;
import com.howe.blog.mapper.BlogLinkMapper;
import com.howe.blog.service.IBlogLinkService;
import com.howe.blog.util.UrlGuard;
import com.howe.common.exception.ServiceException;
import com.howe.common.utils.SecurityUtils;
import com.howe.common.utils.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 博客站点（友链/RSS订阅源） 服务层实现
 *
 * <p>共表带来的越权风险在这里做第二道拦截：Mapper 的 where 已同时约束
 * link_id 与 link_type，本层再比对影响行数——跨类型操作影响 0 行，
 * 若不检查就会静默返回「成功」，让调用方以为改动生效了。</p>
 *
 * @author howe
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BlogLinkServiceImpl implements IBlogLinkService {

    /** 类型：RSS订阅源 */
    private static final String TYPE_FEED = "2";

    private final BlogLinkMapper blogLinkMapper;

    @Override
    public List<BlogLink> selectBlogLinkList(BlogLink blogLink) {
        return blogLinkMapper.selectBlogLinkList(blogLink);
    }

    @Override
    public BlogLink selectBlogLinkById(Long linkId, String linkType) {
        return blogLinkMapper.selectBlogLinkById(linkId, linkType);
    }

    @Override
    public int insertBlogLink(BlogLink blogLink) {
        if (isFeed(blogLink) && StringUtils.isEmpty(blogLink.getRssUrl())) {
            throw new ServiceException("RSS订阅源必须填写订阅地址");
        }
        checkUrls(blogLink);
        if (StringUtils.isEmpty(blogLink.getStatus())) {
            blogLink.setStatus("0");
        }
        blogLink.setCreateBy(SecurityUtils.getUsername());
        return blogLinkMapper.insertBlogLink(blogLink);
    }

    @Override
    public int updateBlogLink(BlogLink blogLink) {
        // 修改是部分更新：rssUrl 为 null 表示本次不改它，只有显式传了空串才是「清空」
        if (isFeed(blogLink) && blogLink.getRssUrl() != null && StringUtils.isEmpty(blogLink.getRssUrl())) {
            throw new ServiceException("RSS订阅源必须填写订阅地址");
        }
        checkUrls(blogLink);
        blogLink.setUpdateBy(SecurityUtils.getUsername());
        int rows = blogLinkMapper.updateBlogLink(blogLink);
        if (rows == 0) {
            throw new ServiceException("数据不存在或类型不匹配");
        }
        return rows;
    }

    /**
     * 校验三个会被拼进页面或被服务端主动请求的地址
     *
     * <p>前端表单虽然也有正则，但那只是体验；直接打接口就能绕过，
     * 所以协议白名单必须在服务端再做一遍——否则 {@code javascript:} 会一路存进库、
     * 再被渲染成可点击的 href。</p>
     *
     * @param blogLink 站点
     */
    private void checkUrls(BlogLink blogLink) {
        UrlGuard.assertDisplayable(blogLink.getLinkUrl(), "站点地址");
        UrlGuard.assertDisplayable(blogLink.getAvatar(), "头像地址");
        // 订阅地址由服务端主动请求，除协议外还要挡内网目标（SSRF）
        if (StringUtils.isNotEmpty(blogLink.getRssUrl())) {
            UrlGuard.assertFetchable(blogLink.getRssUrl());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteBlogLinkByIds(Long[] linkIds, String linkType) {
        if (linkIds == null || linkIds.length == 0) {
            throw new ServiceException("请选择要删除的数据");
        }
        int rows = blogLinkMapper.deleteBlogLinkByIds(linkIds, linkType);
        if (rows != linkIds.length) {
            // 影响行数对不上说明入参里混进了别的类型的 ID，整体判失败
            log.warn("删除站点时类型不匹配：linkType={}，请求{}条，实际{}条", linkType, linkIds.length, rows);
            throw new ServiceException("部分数据不存在或类型不匹配");
        }
        return rows;
    }

    /**
     * 是否 RSS 订阅源
     *
     * @param blogLink 站点
     * @return 结果
     */
    private boolean isFeed(BlogLink blogLink) {
        return TYPE_FEED.equals(blogLink.getLinkType());
    }
}
