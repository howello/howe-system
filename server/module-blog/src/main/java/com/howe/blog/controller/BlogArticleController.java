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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "博客文章", description = "文章索引的增删改查与仓库同步，正文真源在 GitHub 仓库的 markdown 文件")
@RestController
@RequestMapping("/blog/article")
public class BlogArticleController extends BaseController {
    @Autowired
    private IBlogArticleService blogArticleService;

    /**
     * 查询文章列表
     */
    @Operation(summary = "查询文章列表", description = "分页查询本地文章索引，不读取正文")
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
    @Operation(summary = "导出文章列表", description = "按查询条件导出文章索引为 Excel")
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
    @Operation(summary = "获取文章详情", description = "返回索引信息，正文实时从 GitHub 拉取，并把最新的 git_sha 回写索引")
    @PreAuthorize("@ss.hasPermi('blog:article:query')")
    @GetMapping(value = "/{articleId}")
    public AjaxResult getInfo(@Parameter(description = "文章ID（本地索引主键）", required = true)
                              @PathVariable("articleId") Long articleId) {
        return success(blogArticleService.selectBlogArticleById(articleId));
    }

    /**
     * 新增文章
     */
    @Operation(summary = "新增文章", description = "先把 markdown 文件提交到 GitHub 仓库，成功后再写入本地索引")
    @PreAuthorize("@ss.hasPermi('blog:article:add')")
    @Log(title = "博客文章", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Valid @RequestBody BlogArticle blogArticle) {
        return toAjax(blogArticleService.insertBlogArticle(blogArticle));
    }

    /**
     * 修改文章
     */
    @Operation(summary = "修改文章", description = "先更新 GitHub 仓库中的 markdown 文件，成功后再更新本地索引；需带上有效的 git_sha，过期会返回冲突")
    @PreAuthorize("@ss.hasPermi('blog:article:edit')")
    @Log(title = "博客文章", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Valid @RequestBody BlogArticle blogArticle) {
        return toAjax(blogArticleService.updateBlogArticle(blogArticle));
    }

    /**
     * 删除文章（同时删除仓库中的 markdown 文件）
     */
    @Operation(summary = "删除文章", description = "同时删除 GitHub 仓库中的 markdown 文件与本地索引记录")
    @PreAuthorize("@ss.hasPermi('blog:article:remove')")
    @Log(title = "博客文章", businessType = BusinessType.DELETE)
    @DeleteMapping("/{articleIds}")
    public AjaxResult remove(@Parameter(description = "文章ID数组，多个用逗号分隔", required = true)
                             @PathVariable Long[] articleIds) {
        return toAjax(blogArticleService.deleteBlogArticleByIds(articleIds));
    }

    /**
     * 从 GitHub 全量重建索引
     */
    @Operation(summary = "同步仓库", description = "走 Git Trees API 一次拿整棵树全量比对，sha 未变的文件跳过不读内容，返回新增/更新/删除/跳过的统计")
    @PreAuthorize("@ss.hasPermi('blog:article:sync')")
    @Log(title = "博客文章", businessType = BusinessType.OTHER)
    @PostMapping("/sync")
    public AjaxResult sync() {
        BlogSyncResult result = blogArticleService.syncFromGithub();
        AjaxResult ajax = AjaxResult.success("同步完成", result);
        return ajax;
    }
}
