package com.howe.blog.service.impl;

import com.howe.blog.domain.BlogArticle;
import com.howe.blog.domain.BlogDraft;
import com.howe.blog.mapper.BlogDraftMapper;
import com.howe.blog.service.IBlogArticleService;
import com.howe.blog.service.IBlogDraftService;
import com.howe.common.exception.ServiceException;
import com.howe.common.utils.SecurityUtils;
import com.howe.common.utils.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

/**
 * 博客草稿 服务层实现
 *
 * <p>
 * 草稿只存在本地库里，不进 GitHub——blog-ui 的 {@code hide: true} 不是草稿标记，
 * 它只把文章从首页、RSS 和上下篇里隐去，文章页和分类页依旧可访问。
 * </p>
 *
 * @author howe
 */
@Service
public class BlogDraftServiceImpl implements IBlogDraftService
{
    @Autowired
    private BlogDraftMapper blogDraftMapper;

    @Autowired
    private IBlogArticleService blogArticleService;

    @Override
    public List<BlogDraft> selectBlogDraftList(BlogDraft blogDraft)
    {
        return blogDraftMapper.selectBlogDraftList(blogDraft);
    }

    @Override
    public BlogDraft selectBlogDraftById(Long draftId)
    {
        return blogDraftMapper.selectBlogDraftById(draftId);
    }

    @Override
    public int insertBlogDraft(BlogDraft blogDraft)
    {
        if (StringUtils.isEmpty(blogDraft.getStatus()))
        {
            blogDraft.setStatus("0");
        }
        blogDraft.setCreateBy(SecurityUtils.getUsername());
        return blogDraftMapper.insertBlogDraft(blogDraft);
    }

    @Override
    public int updateBlogDraft(BlogDraft blogDraft)
    {
        BlogDraft existing = blogDraftMapper.selectBlogDraftById(blogDraft.getDraftId());
        if (existing == null)
        {
            throw new ServiceException("草稿不存在或已被删除");
        }
        if ("1".equals(existing.getStatus()))
        {
            throw new ServiceException("草稿已发布，请到文章管理里修改");
        }
        blogDraft.setUpdateBy(SecurityUtils.getUsername());
        return blogDraftMapper.updateBlogDraft(blogDraft);
    }

    @Override
    public int deleteBlogDraftByIds(Long[] draftIds)
    {
        return blogDraftMapper.deleteBlogDraftByIds(draftIds);
    }

    @Override
    @Transactional
    public int publishDraft(Long draftId, String filePath)
    {
        BlogDraft draft = blogDraftMapper.selectBlogDraftById(draftId);
        if (draft == null)
        {
            throw new ServiceException("草稿不存在或已被删除");
        }
        if ("1".equals(draft.getStatus()))
        {
            throw new ServiceException("该草稿已经发布过了");
        }
        if (StringUtils.isEmpty(draft.getSlug()))
        {
            throw new ServiceException("发布前请先填写文章标识（决定文章 URL）");
        }
        if (StringUtils.isEmpty(draft.getCategories()))
        {
            throw new ServiceException("发布前请先填写分类");
        }

        BlogArticle article = new BlogArticle();
        article.setSlug(draft.getSlug());
        article.setTitle(draft.getTitle());
        article.setContent(draft.getContent());
        article.setCategories(draft.getCategories());
        article.setTags(draft.getTags());
        article.setCover(draft.getCover());
        article.setPublishDate(draft.getPublishDate() == null ? new Date() : draft.getPublishDate());
        article.setRecommend(draft.getRecommend());
        article.setHide(draft.getHide());
        article.setIsTop(draft.getIsTop());
        article.setFilePath(StringUtils.isNotEmpty(filePath) ? filePath : draft.getPublishedPath());
        // 先提交到 GitHub，成功后再回写草稿状态；失败会抛异常回滚，草稿保持未发布
        blogArticleService.insertBlogArticle(article);

        BlogDraft patch = new BlogDraft();
        patch.setDraftId(draftId);
        patch.setStatus("1");
        patch.setPublishedPath(article.getFilePath());
        patch.setPublishTime(new Date());
        patch.setUpdateBy(SecurityUtils.getUsername());
        return blogDraftMapper.updateBlogDraft(patch);
    }
}
