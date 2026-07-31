package com.howe.blog.controller;

import com.howe.blog.domain.BlogDraft;
import com.howe.blog.service.IBlogDraftService;
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
 * 博客草稿 控制层
 *
 * @author howe
 */
@Tag(name = "博客草稿", description = "草稿只存在本地 blog_draft 表，不进 GitHub 仓库，点发布才生成 markdown 提交")
@RestController
@RequestMapping("/blog/draft")
public class BlogDraftController extends BaseController
{
    @Autowired
    private IBlogDraftService blogDraftService;

    /**
     * 查询草稿列表
     */
    @Operation(summary = "查询草稿列表", description = "分页查询本地草稿")
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
    @Operation(summary = "导出草稿列表", description = "按查询条件导出草稿为 Excel")
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
    @Operation(summary = "获取草稿详情", description = "按草稿ID查询单条草稿，含 markdown 正文")
    @PreAuthorize("@ss.hasPermi('blog:draft:query')")
    @GetMapping(value = "/{draftId}")
    public AjaxResult getInfo(@Parameter(description = "草稿ID", required = true)
            @PathVariable("draftId") Long draftId)
    {
        return success(blogDraftService.selectBlogDraftById(draftId));
    }

    /**
     * 新增草稿
     */
    @Operation(summary = "新增草稿", description = "只写本地表，不会向 GitHub 仓库提交任何内容")
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
    @Operation(summary = "修改草稿", description = "只更新本地表，不会向 GitHub 仓库提交任何内容")
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
    @Operation(summary = "删除草稿", description = "只删除本地草稿记录，已发布的仓库文件不受影响")
    @PreAuthorize("@ss.hasPermi('blog:draft:remove')")
    @Log(title = "博客草稿", businessType = BusinessType.DELETE)
    @DeleteMapping("/{draftIds}")
    public AjaxResult remove(@Parameter(description = "草稿ID数组，多个用逗号分隔", required = true)
            @PathVariable Long[] draftIds)
    {
        return toAjax(blogDraftService.deleteBlogDraftByIds(draftIds));
    }

    /**
     * 发布草稿：生成 markdown 提交到 GitHub
     *
     * @param draftId 草稿ID
     * @param filePath 目标文件路径，可留空（默认用 标识.md）
     */
    @Operation(summary = "发布草稿", description = "把草稿拼成带 frontmatter 的 markdown 提交到 GitHub 仓库，成功后更新草稿状态与文章索引")
    @PreAuthorize("@ss.hasPermi('blog:draft:publish')")
    @Log(title = "博客草稿", businessType = BusinessType.OTHER)
    @PostMapping("/publish/{draftId}")
    public AjaxResult publish(@Parameter(description = "草稿ID", required = true)
            @PathVariable("draftId") Long draftId,
            @Parameter(description = "仓库内目标文件路径，可留空，留空时默认用「文章标识.md」")
            @RequestParam(value = "filePath", required = false) String filePath)
    {
        return toAjax(blogDraftService.publishDraft(draftId, filePath));
    }
}
