package com.howe.blog.controller;

import com.howe.blog.domain.BlogArticle;
import com.howe.blog.domain.vo.BlogSyncResult;
import com.howe.blog.service.IBlogArticleService;
import com.howe.common.annotation.Log;
import com.howe.common.core.controller.BaseController;
import com.howe.common.core.domain.AjaxResult;
import com.howe.common.core.page.TableDataInfo;
import com.howe.common.enums.BusinessType;
import com.howe.common.utils.poi.ExcelUtil;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 博客文章 控制层
 *
 * @author howe
 */
@RestController
@RequestMapping("/blog/article")
public class BlogArticleController extends BaseController {
    @Autowired
    private IBlogArticleService blogArticleService;

    /**
     * 查询文章列表
     */
    @PreAuthorize("@ss.hasPermi('blog:article:list')")
    @GetMapping("/list")
    public TableDataInfo list(BlogArticle blogArticle) {
        startPage();
        List<BlogArticle> list = blogArticleService.selectBlogArticleList(blogArticle);
        return getDataTable(list);
    }

    /**
     * 导出文章列表
     */
    @PreAuthorize("@ss.hasPermi('blog:article:export')")
    @Log(title = "博客文章", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, BlogArticle blogArticle) {
        List<BlogArticle> list = blogArticleService.selectBlogArticleList(blogArticle);
        ExcelUtil<BlogArticle> util = new ExcelUtil<BlogArticle>(BlogArticle.class);
        util.exportExcel(response, list, "博客文章数据");
    }

    /**
     * 获取文章详情（正文实时从 GitHub 拉取）
     */
    @PreAuthorize("@ss.hasPermi('blog:article:query')")
    @GetMapping(value = "/{articleId}")
    public AjaxResult getInfo(@PathVariable("articleId") Long articleId) {
        return success(blogArticleService.selectBlogArticleById(articleId));
    }

    /**
     * 新增文章
     */
    @PreAuthorize("@ss.hasPermi('blog:article:add')")
    @Log(title = "博客文章", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Valid @RequestBody BlogArticle blogArticle) {
        return toAjax(blogArticleService.insertBlogArticle(blogArticle));
    }

    /**
     * 修改文章
     */
    @PreAuthorize("@ss.hasPermi('blog:article:edit')")
    @Log(title = "博客文章", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Valid @RequestBody BlogArticle blogArticle) {
        return toAjax(blogArticleService.updateBlogArticle(blogArticle));
    }

    /**
     * 删除文章（同时删除仓库中的 markdown 文件）
     */
    @PreAuthorize("@ss.hasPermi('blog:article:remove')")
    @Log(title = "博客文章", businessType = BusinessType.DELETE)
    @DeleteMapping("/{articleIds}")
    public AjaxResult remove(@PathVariable Long[] articleIds) {
        return toAjax(blogArticleService.deleteBlogArticleByIds(articleIds));
    }

    /**
     * 从 GitHub 全量重建索引
     */
    @PreAuthorize("@ss.hasPermi('blog:article:sync')")
    @Log(title = "博客文章", businessType = BusinessType.OTHER)
    @PostMapping("/sync")
    public AjaxResult sync() {
        BlogSyncResult result = blogArticleService.syncFromGithub();
        AjaxResult ajax = AjaxResult.success("同步完成", result);
        return ajax;
    }
}
