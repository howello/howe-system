package com.howe.blog.service.impl;

import com.howe.blog.domain.BlogTalk;
import com.howe.blog.mapper.BlogTalkMapper;
import com.howe.blog.service.IBlogTalkService;
import com.howe.common.exception.ServiceException;
import com.howe.common.utils.SecurityUtils;
import com.howe.common.utils.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

/**
 * 博客说说 服务层实现
 *
 * <p>正文全程按 markdown 原文透传，不做转义、不做 HTML 转换——
 * 只要在这里动一次正文，管理端重新打开就再也拿不回原始语法。</p>
 *
 * @author howe
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BlogTalkServiceImpl implements IBlogTalkService {

    /** 状态：发布 */
    private static final String STATUS_PUBLISHED = "0";

    /** 置顶：否 */
    private static final String TOP_NO = "0";

    private final BlogTalkMapper blogTalkMapper;

    @Override
    public List<BlogTalk> selectBlogTalkList(BlogTalk blogTalk) {
        return blogTalkMapper.selectBlogTalkList(blogTalk);
    }

    @Override
    public BlogTalk selectBlogTalkById(Long talkId) {
        return blogTalkMapper.selectBlogTalkById(talkId);
    }

    @Override
    public int insertBlogTalk(BlogTalk blogTalk) {
        if (StringUtils.isEmpty(blogTalk.getStatus())) {
            blogTalk.setStatus(STATUS_PUBLISHED);
        }
        if (StringUtils.isEmpty(blogTalk.getIsTop())) {
            blogTalk.setIsTop(TOP_NO);
        }
        // 发布时间是排序键，留空会让该条永远沉底，这里补当前时间
        if (blogTalk.getPubDate() == null) {
            blogTalk.setPubDate(new Date());
        }
        blogTalk.setCreateBy(SecurityUtils.getUsername());
        return blogTalkMapper.insertBlogTalk(blogTalk);
    }

    @Override
    public int updateBlogTalk(BlogTalk blogTalk) {
        blogTalk.setUpdateBy(SecurityUtils.getUsername());
        int rows = blogTalkMapper.updateBlogTalk(blogTalk);
        if (rows == 0) {
            throw new ServiceException("数据不存在");
        }
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteBlogTalkByIds(Long[] talkIds) {
        if (talkIds == null || talkIds.length == 0) {
            throw new ServiceException("请选择要删除的数据");
        }
        int rows = blogTalkMapper.deleteBlogTalkByIds(talkIds);
        if (rows != talkIds.length) {
            log.warn("删除说说时部分数据不存在：请求{}条，实际{}条", talkIds.length, rows);
            throw new ServiceException("部分数据不存在");
        }
        return rows;
    }
}
