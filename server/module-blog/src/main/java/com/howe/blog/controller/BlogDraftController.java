package com.howe.blog.controller;

import com.howe.blog.domain.BlogDraft;
import com.howe.blog.service.IBlogDraftService;
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
 * 博客草稿 控制层
 *
 * @author howe
 */
@RestController
@RequestMapping("/blog/draft")
public class BlogDraftController extends BaseController
{
    @Autowired
    private IBlogDraftService blogDraftService;

    /**
     * 查询草稿列表
     */
    @PreAuthorize("@ss.hasPermi('blog:draft:list')")
    @GetMapping("/list")
    public TableDataInfo list(BlogDraft blogDraft)
    {
        startPage();
        List<BlogDraft> list = blogDraftService.selectBlogDraftList(blogDraft);
        return getDataTable(list);
    }

    /**
     * 导出草稿列表
     */
    @PreAuthorize("@ss.hasPermi('blog:draft:list')")
    @Log(title = "博客草稿", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, BlogDraft blogDraft)
    {
        List<BlogDraft> list = blogDraftService.selectBlogDraftList(blogDraft);
        ExcelUtil<BlogDraft> util = new ExcelUtil<BlogDraft>(BlogDraft.class);
        util.exportExcel(response, list, "博客草稿数据");
    }

    /**
     * 获取草稿详情
     */
    @PreAuthorize("@ss.hasPermi('blog:draft:query')")
    @GetMapping(value = "/{draftId}")
    public AjaxResult getInfo(@PathVariable("draftId") Long draftId)
    {
        return success(blogDraftService.selectBlogDraftById(draftId));
    }

    /**
     * 新增草稿
     */
    @PreAuthorize("@ss.hasPermi('blog:draft:add')")
    @Log(title = "博客草稿", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Valid @RequestBody BlogDraft blogDraft)
    {
        return toAjax(blogDraftService.insertBlogDraft(blogDraft));
    }

    /**
     * 修改草稿
     */
    @PreAuthorize("@ss.hasPermi('blog:draft:edit')")
    @Log(title = "博客草稿", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Valid @RequestBody BlogDraft blogDraft)
    {
        return toAjax(blogDraftService.updateBlogDraft(blogDraft));
    }

    /**
     * 删除草稿
     */
    @PreAuthorize("@ss.hasPermi('blog:draft:remove')")
    @Log(title = "博客草稿", businessType = BusinessType.DELETE)
    @DeleteMapping("/{draftIds}")
    public AjaxResult remove(@PathVariable Long[] draftIds)
    {
        return toAjax(blogDraftService.deleteBlogDraftByIds(draftIds));
    }

    /**
     * 发布草稿：生成 markdown 提交到 GitHub
     *
     * @param draftId 草稿ID
     * @param filePath 目标文件路径，可留空（默认用 标识.md）
     */
    @PreAuthorize("@ss.hasPermi('blog:draft:publish')")
    @Log(title = "博客草稿", businessType = BusinessType.OTHER)
    @PostMapping("/publish/{draftId}")
    public AjaxResult publish(@PathVariable("draftId") Long draftId,
            @RequestParam(value = "filePath", required = false) String filePath)
    {
        return toAjax(blogDraftService.publishDraft(draftId, filePath));
    }
}
